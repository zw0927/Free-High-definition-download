param(
    [ValidatePattern('^[^\\/:*?"<>|]+$')]
    [string]$ShortcutName = 'Free HD Download'
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    throw 'The current Windows account does not have a local application directory.'
}

$packageRoot = (Get-Item -LiteralPath $PSScriptRoot).FullName
$programsDirectory = Join-Path $env:LOCALAPPDATA 'Programs'
$installDirectory = Join-Path $programsDirectory 'FreeHDDownload'
$launcherName = 'FreeHDDownload.exe'
$launcherPath = Join-Path $installDirectory $launcherName
$packageEntries = @('app', 'runtime', 'licenses', $launcherName, 'FreeHDDownload.ico',
    'README.txt', 'LICENSE', 'NOTICE', 'THIRD_PARTY_NOTICES.txt')

if ($packageRoot -ne $installDirectory) {
    foreach ($entry in $packageEntries) {
        if (!(Test-Path -LiteralPath (Join-Path $packageRoot $entry))) {
            throw ('Extract the complete Windows ZIP before installing. Missing: ' + $entry)
        }
    }

    $running = Get-Process -Name 'FreeHDDownload' -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -eq $launcherPath }
    if ($running) {
        throw 'Close FreeHDDownload before updating the installed copy.'
    }

    New-Item -ItemType Directory -Path $installDirectory -Force | Out-Null
    foreach ($entry in $packageEntries) {
        Copy-Item -LiteralPath (Join-Path $packageRoot $entry) -Destination $installDirectory -Recurse -Force
    }
}

$desktopDirectory = [Environment]::GetFolderPath('DesktopDirectory')
$shortcutPath = Join-Path $desktopDirectory ($ShortcutName + '.lnk')
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $launcherPath
$shortcut.WorkingDirectory = $installDirectory
$shortcut.Description = 'Free HD Download'
$shortcut.Save()

Write-Output ('Installed: ' + $launcherPath)
Write-Output ('Desktop shortcut: ' + $shortcutPath)
