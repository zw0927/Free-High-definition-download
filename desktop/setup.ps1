$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$toolDirectory = Join-Path $projectDirectory '.tools\desktop'
$jdkDirectory = Join-Path $toolDirectory 'jdk'
if (Test-Path -LiteralPath $jdkDirectory) {
    Write-Output 'Temurin JDK is already present.'
    exit 0
}
New-Item -ItemType Directory -Path $toolDirectory -Force | Out-Null
$assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse'
$package = $assets[0].binary.package
$archive = Join-Path $toolDirectory 'temurin.zip'
Invoke-WebRequest -Uri $package.link -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $package.checksum) {
    throw 'Temurin checksum mismatch.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $jdkDirectory
Remove-Item -LiteralPath $archive
Write-Output 'Temurin JDK downloaded and checksum verified.'
