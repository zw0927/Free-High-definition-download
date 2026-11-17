$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$toolDirectory = Join-Path $projectDirectory '.tools'
New-Item -ItemType Directory -Path $toolDirectory -Force | Out-Null
$packages = @(
    @{ Name = 'platform'; Url = 'https://dl.google.com/android/repository/platform-35_r02.zip' },
    @{ Name = 'build-tools'; Url = 'https://dl.google.com/android/repository/build-tools_r35_windows.zip' },
    @{ Name = 'platform-tools'; Url = 'https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip' }
)
foreach ($package in $packages) {
    $destination = Join-Path $toolDirectory $package.Name
    if (Test-Path -LiteralPath $destination) {
        continue
    }
    $archive = Join-Path $toolDirectory ($package.Name + '.zip')
    Invoke-WebRequest -Uri $package.Url -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $destination
    Remove-Item -LiteralPath $archive
}
Write-Output 'Android build tools are ready.'
