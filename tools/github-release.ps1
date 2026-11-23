param([switch]$CreateRepository, [switch]$PreviewRelease, [switch]$PublishRelease,
      [switch]$MakePublic, [long]$ResumeReleaseId = 0, [string]$Version = '1.2.4')
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    throw 'Version must use major.minor.patch format.'
}
if ($PreviewRelease -and ($PublishRelease -or $CreateRepository)) {
    throw 'PreviewRelease is read-only and cannot be combined with write actions.'
}
if ($MakePublic -and !$PublishRelease) {
    throw 'MakePublic requires PublishRelease.'
}
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
if ($PreviewRelease) {
    $previewAssets = @(('FreeHDDownload-' + $Version + '-android.apk'), ('FreeHDDownload-' + $Version + '-windows-x64.zip'), ('FreeHDDownload-' + $Version + '-source.zip'), 'SHA256SUMS.txt')
    $checksumLines = Get-Content -LiteralPath (Join-Path $projectDirectory 'release\SHA256SUMS.txt')
    foreach ($asset in $previewAssets) {
        $file = Join-Path $projectDirectory ('release\' + $asset)
        if (!(Test-Path -LiteralPath $file -PathType Leaf)) {
            throw ('Preview asset is missing: ' + $asset)
        }
        if ($asset -ne 'SHA256SUMS.txt') {
            $hash = Get-FileHash -LiteralPath $file -Algorithm SHA256
            if ($checksumLines -notcontains ($hash.Hash.ToLowerInvariant() + '  ' + $asset)) {
                throw ('Preview checksum does not match: ' + $asset)
            }
        }
        Write-Output ($asset + '  ' + (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant())
    }
    $previewCommit = git -c ('safe.directory=' + $projectDirectory.Replace('\', '/')) -C $projectDirectory rev-parse HEAD
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot identify the preview source commit.'
    }
    Write-Output ('Read-only release preview; commit=' + $previewCommit.Trim() + '; no GitHub changes were made.')
    exit 0
}
if ($PublishRelease) {
    $assets = @(('FreeHDDownload-' + $Version + '-android.apk'), ('FreeHDDownload-' + $Version + '-windows-x64.zip'), ('FreeHDDownload-' + $Version + '-source.zip'), 'SHA256SUMS.txt')
    foreach ($asset in $assets) {
        $file = Join-Path $projectDirectory ('release\' + $asset)
        if (!(Test-Path -LiteralPath $file -PathType Leaf)) {
            throw ('Release asset is missing: ' + $asset)
        }
    }
    if ($PreviewRelease) {
        Write-Output ('Verified release preview for v' + $Version + '; no GitHub changes made.')
        Write-Output ('Commit: ' + (git -c ('safe.directory=' + $projectDirectory.Replace('\', '/')) -C $projectDirectory rev-parse HEAD))
        foreach ($asset in $assets) {
            $file = Join-Path $projectDirectory ('release\' + $asset)
            $hash = Get-FileHash -LiteralPath $file -Algorithm SHA256
            Write-Output ($asset + '  ' + $hash.Hash.ToLowerInvariant())
        }
        exit 0
    }
    $checksumLines = Get-Content -LiteralPath (Join-Path $projectDirectory 'release\SHA256SUMS.txt')
    foreach ($asset in $assets) {
        if ($asset -eq 'SHA256SUMS.txt') {
            continue
        }
        $hash = Get-FileHash -LiteralPath (Join-Path $projectDirectory ('release\' + $asset)) -Algorithm SHA256
        $expected = $hash.Hash.ToLowerInvariant() + '  ' + $asset
        if ($checksumLines -notcontains $expected) {
            throw ('Release checksum does not match: ' + $asset)
        }
    }
    $commit = git -c ('safe.directory=' + $projectDirectory.Replace('\', '/')) -C $projectDirectory rev-parse HEAD
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot identify the release source commit.'
    }
    $releaseBody = @{
        tag_name = 'v' + $Version
        target_commitish = $commit.Trim()
        name = 'v' + $Version + ' - Live Photo and image format fixes'
        body = [IO.File]::ReadAllText((Join-Path $projectDirectory 'docs\RELEASE_NOTES.md'))
        draft = $true
        prerelease = $false
    } | ConvertTo-Json
    if ($ResumeReleaseId -gt 0) {
        $release = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases/' + $ResumeReleaseId) -Headers $headers
        if (!$release.draft) {
            throw 'Resume is only supported for a draft release.'
        }
        if ($release.tag_name -ne ('v' + $Version)) {
            throw 'Draft release version does not match the requested version.'
        }
    } else {
        try {
            $release = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases/tags/v' + $Version) -Headers $headers
            if (!$release.draft) {
                throw ('Version v' + $Version + ' is already published and will not be overwritten.')
            }
            Write-Output ('Resuming existing draft: ' + $release.html_url)
        } catch {
            $statusCode = 0
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            if ($statusCode -ne 404) {
                throw
            }
            try {
                $reference = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/git/ref/tags/v' + $Version) -Headers $headers
                if ($reference) {
                    throw ('Git tag v' + $Version + ' already exists without a reusable draft release.')
                }
            } catch {
                $referenceStatus = 0
                if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                    $referenceStatus = [int]$_.Exception.Response.StatusCode
                }
                if ($referenceStatus -ne 404) {
                    throw
                }
            }
            $release = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases') -Headers $headers -Method Post -ContentType 'application/json' -Body $releaseBody
        }
    }
    $uploadBase = $release.upload_url -replace '\{.*$', ''
    foreach ($asset in $assets) {
        $existing = $release.assets | Where-Object { $_.name -eq $asset -and $_.state -eq 'uploaded' }
        if ($existing) {
            $localHash = Get-FileHash -LiteralPath (Join-Path $projectDirectory ('release\' + $asset)) -Algorithm SHA256
            $expectedDigest = 'sha256:' + $localHash.Hash.ToLowerInvariant()
            if ($existing.digest -ne $expectedDigest) {
                throw ('Existing draft asset differs from the local file: ' + $asset)
            }
            Write-Output ('Already uploaded: ' + $asset)
            continue
        }
        $file = Join-Path $projectDirectory ('release\' + $asset)
        $uri = $uploadBase + '?name=' + [Uri]::EscapeDataString($asset)
        $configuration = 'header = "Authorization: Bearer ' + $credential['password'] + '"'
        $response = $configuration | curl.exe --config - --http1.1 --connect-timeout 15 --max-time 600 --fail-with-body --silent --show-error --request POST --header 'Content-Type: application/octet-stream' --header 'Expect:' --limit-rate 1M --data-binary ('@' + $file) $uri
        if ($LASTEXITCODE -ne 0) {
            throw ('Asset upload failed: ' + $asset)
        }
        $uploaded = $response | ConvertFrom-Json
        $localHash = Get-FileHash -LiteralPath $file -Algorithm SHA256
        if ($uploaded.digest -ne ('sha256:' + $localHash.Hash.ToLowerInvariant())) {
            throw ('Uploaded asset checksum does not match: ' + $asset)
        }
        Write-Output ('Uploaded: ' + $uploaded.name + ' (' + $uploaded.size + ' bytes)')
    }
    if ($MakePublic) {
        $published = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repositoryPath + '/releases/' + $release.id) -Headers $headers -Method Patch -ContentType 'application/json' -Body '{"draft":false}'
        Write-Output ('Published release: ' + $published.html_url)
    } else {
        Write-Output ('Draft release assets uploaded and SHA256-verified; it remains private until explicitly confirmed: ' + $release.html_url)
    }
}
