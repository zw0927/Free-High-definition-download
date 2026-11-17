param([switch]$KeepBuild)
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$toolDirectory = Join-Path $projectDirectory '.tools'
$buildDirectory = Join-Path $projectDirectory 'build'
$outputDirectory = Join-Path $projectDirectory 'release'
$signingDirectory = Join-Path $projectDirectory 'signing'
$javaDirectory = 'C:\Program Files\Java\jdk-17\bin'
$androidJar = Join-Path $toolDirectory 'platform\android-35\android.jar'
$buildTools = Join-Path $toolDirectory 'build-tools\android-15'
foreach ($directory in @($buildDirectory, $outputDirectory, $signingDirectory)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = $javaDirectory + ';' + $env:PATH

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ('Build command failed: ' + $Executable)
    }
}

$resourceZip = Join-Path $buildDirectory 'resources.zip'
Invoke-Checked (Join-Path $buildTools 'aapt2.exe') @('compile', '--dir', (Join-Path $projectDirectory 'res'), '-o', $resourceZip)
$generated = Join-Path $buildDirectory 'generated'
New-Item -ItemType Directory -Path $generated -Force | Out-Null
$unsigned = Join-Path $buildDirectory 'unsigned.apk'
Invoke-Checked (Join-Path $buildTools 'aapt2.exe') @('link', '-o', $unsigned, '-I', $androidJar, '--manifest', (Join-Path $projectDirectory 'AndroidManifest.xml'), '--java', $generated, '-A', (Join-Path $projectDirectory 'assets'), $resourceZip)
$classes = Join-Path $buildDirectory 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'src'), $generated -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Invoke-Checked (Join-Path $javaDirectory 'javac.exe') (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', $androidJar, '-d', $classes) + $sources)
$classJar = Join-Path $buildDirectory 'classes.jar'
Invoke-Checked (Join-Path $javaDirectory 'jar.exe') @('cf', $classJar, '-C', $classes, '.')
$dex = Join-Path $buildDirectory 'dex'
New-Item -ItemType Directory -Path $dex -Force | Out-Null
Invoke-Checked (Join-Path $buildTools 'd8.bat') @('--lib', $androidJar, '--min-api', '26', '--output', $dex, $classJar)
Invoke-Checked (Join-Path $javaDirectory 'jar.exe') @('uf', $unsigned, '-C', $dex, 'classes.dex')
$aligned = Join-Path $buildDirectory 'aligned.apk'
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-f', '-p', '4', $unsigned, $aligned)
$keystore = Join-Path $signingDirectory 'dousave.keystore'
$keystorePassword = $env:FHD_KEYSTORE_PASSWORD
if (!(Test-Path -LiteralPath $keystore)) {
    if ([string]::IsNullOrEmpty($keystorePassword)) {
        $keystorePassword = 'android-development-only'
    }
    Invoke-Checked (Join-Path $javaDirectory 'keytool.exe') @('-genkeypair', '-keystore', $keystore, '-storepass', $keystorePassword, '-keypass', $keystorePassword, '-alias', 'dousave', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=FreeHDDownload Development, O=Local Build, C=CN')
} elseif ([string]::IsNullOrEmpty($keystorePassword)) {
    throw 'Set FHD_KEYSTORE_PASSWORD to use an existing private signing key.'
}
$apk = Join-Path $outputDirectory '无水印下载-1.2.1.apk'
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('sign', '--v4-signing-enabled', 'false', '--ks', $keystore, '--ks-key-alias', 'dousave', '--ks-pass', ('pass:' + $keystorePassword), '--key-pass', ('pass:' + $keystorePassword), '--out', $apk, $aligned)
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('verify', '--verbose', $apk)
Get-FileHash -LiteralPath $apk -Algorithm SHA256
Write-Output ('APK: ' + $apk)
if (!$KeepBuild) {
    $resolvedBuild = [System.IO.Path]::GetFullPath($buildDirectory)
    $resolvedProject = [System.IO.Path]::GetFullPath($projectDirectory) + [System.IO.Path]::DirectorySeparatorChar
    if (!$resolvedBuild.StartsWith($resolvedProject, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove a build directory outside this project.'
    }
    Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
}
