param([switch]$CreateRepository, [switch]$PublishRelease, [long]$ResumeReleaseId = 0)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$repositoryName = 'Free-High-definition-download'
$env:GIT_TERMINAL_PROMPT = '0'
$credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$credential = @{}
foreach ($line in $credentialLines) {
    $parts = $line -split '=', 2
    if ($parts.Length -eq 2) {
        $credential[$parts[0]] = $parts[1]
    }
}
if (!$credential.ContainsKey('password')) {
    throw 'Sign into GitHub through Git Credential Manager first.'
}
$headers = @{
    Authorization = 'Bearer ' + $credential['password']
    Accept = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
}
$account = Invoke-RestMethod -Uri 'https://api.github.com/user' -Headers $headers
$repositoryPath = $account.login + '/' + $repositoryName
if ($CreateRepository) {
    $body = @{
        name = $repositoryName
        description = 'Android and Windows app for saving public videos, images and article text from Douyin, Xiaohongshu and Bilibili.'
        private = $false
        auto_init = $false
        has_issues = $true
        has_wiki = $false
    } | ConvertTo-Json
    $created = Invoke-RestMethod -Uri 'https://api.github.com/user/repos' -Headers $headers -Method Post -ContentType 'application/json' -Body $body
    Write-Output ('Created: ' + $created.html_url)
}
if ($PublishRelease) {
    $releaseBody = @{
        tag_name = 'v1.2.1'
        target_commitish = 'main'
        name = 'v1.2.1 - Android + Windows'
        body = [IO.File]::ReadAllText((Join-Path $projectDirectory 'docs\RELEASE_NOTES.md'))
        draft = $true
        prerelease = $false
    } | ConvertTo-Json
    if ($ResumeReleaseId -gt 0) {
        $release = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases/' + $ResumeReleaseId) -Headers $headers
        if (!$release.draft) {
            throw 'Resume is only supported for a draft release.'
        }
    } else {
        $release = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases') -Headers $headers -Method Post -ContentType 'application/json' -Body $releaseBody
    }
    $uploadBase = $release.upload_url -replace '\{.*$', ''
    $assets = @('FreeHDDownload-1.2.1-android.apk', 'FreeHDDownload-1.2.1-windows-x64.zip', 'FreeHDDownload-1.2.1-source.zip', 'SHA256SUMS.txt')
    foreach ($asset in $assets) {
        $existing = $release.assets | Where-Object { $_.name -eq $asset -and $_.state -eq 'uploaded' }
        if ($existing) {
            Write-Output ('Already uploaded: ' + $asset)
            continue
        }
        $file = Join-Path $projectDirectory ('release\' + $asset)
        $uri = $uploadBase + '?name=' + [Uri]::EscapeDataString($asset)
        $configuration = 'header = "Authorization: Bearer ' + $credential['password'] + '"'
        $response = $configuration | curl.exe --config - --http1.1 --max-time 180 --retry 1 --fail-with-body --silent --show-error --request POST --header 'Content-Type: application/octet-stream' --data-binary ('@' + $file) $uri
        if ($LASTEXITCODE -ne 0) {
            throw ('Asset upload failed: ' + $asset)
        }
        $uploaded = $response | ConvertFrom-Json
        Write-Output ('Uploaded: ' + $uploaded.name + ' (' + $uploaded.size + ' bytes)')
    }
    $published = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases/' + $release.id) -Headers $headers -Method Patch -ContentType 'application/json' -Body '{"draft":false}'
    Write-Output ('Release: ' + $published.html_url)
}
