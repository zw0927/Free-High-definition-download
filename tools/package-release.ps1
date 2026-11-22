param([string]$Version = '1.2.3')
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    throw 'Version must use major.minor.patch format.'
}
$projectDirectory = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectDirectory 'release'
$sourceArchive = Join-Path $outputDirectory ('FreeHDDownload-' + $Version + '-source.zip')
& git -c ('safe.directory=' + $projectDirectory.Replace('\', '/')) -C $projectDirectory archive --format=zip --prefix=Free-High-definition-download/ -o $sourceArchive HEAD
if ($LASTEXITCODE -ne 0) {
    throw 'Source archive failed.'
}
$names = @(('FreeHDDownload-' + $Version + '-android.apk'), ('FreeHDDownload-' + $Version + '-windows-x64.zip'), ('FreeHDDownload-' + $Version + '-source.zip'))
$checksums = @()
foreach ($name in $names) {
    $hash = Get-FileHash -LiteralPath (Join-Path $outputDirectory $name) -Algorithm SHA256
    $checksums += $hash.Hash.ToLowerInvariant() + '  ' + $name
}
$encoding = [System.Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllLines((Join-Path $outputDirectory 'SHA256SUMS.txt'), $checksums, $encoding)
$checksums
