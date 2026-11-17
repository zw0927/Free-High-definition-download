$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectDirectory 'release'
$sourceArchive = Join-Path $outputDirectory 'FreeHDDownload-1.2.1-source.zip'
& git -c ('safe.directory=' + $projectDirectory.Replace('\', '/')) -C $projectDirectory archive --format=zip --prefix=Free-High-definition-download/ -o $sourceArchive HEAD
if ($LASTEXITCODE -ne 0) {
    throw 'Source archive failed.'
}
$names = @('FreeHDDownload-1.2.1-android.apk', 'FreeHDDownload-1.2.1-windows-x64.zip', 'FreeHDDownload-1.2.1-source.zip')
$checksums = @()
foreach ($name in $names) {
    $hash = Get-FileHash -LiteralPath (Join-Path $outputDirectory $name) -Algorithm SHA256
    $checksums += $hash.Hash.ToLowerInvariant() + '  ' + $name
}
$encoding = [System.Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllLines((Join-Path $outputDirectory 'SHA256SUMS.txt'), $checksums, $encoding)
$checksums
