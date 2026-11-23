param([switch]$KeepBuild, [string]$Packager = '', [string]$JdkBin = '')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$dependencyDirectory = Join-Path $projectDirectory '.tools\desktop\lib'
$workDirectory = Join-Path $projectDirectory 'build\windows'
$inputDirectory = Join-Path $workDirectory 'input'
$classesDirectory = Join-Path $workDirectory 'production-classes'
$outputDirectory = Join-Path $projectDirectory 'release\windows'
$jdkRoot = Join-Path $projectDirectory '.tools\desktop\jdk'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $jdk) {
    throw 'Run desktop/setup.ps1 first to download a redistributable Temurin JDK.'
}
$javaDirectory = Join-Path $jdk.FullName 'bin'
if (!$JdkBin.Equals('')) {
    $resolvedJdkBin = [IO.Path]::GetFullPath($JdkBin)
    foreach ($tool in @('javac.exe', 'jar.exe', 'jlink.exe', 'jpackage.exe')) {
        if (!(Test-Path -LiteralPath (Join-Path $resolvedJdkBin $tool))) {
            throw ('Requested JDK tool is missing: ' + $tool)
        }
    }
    $javaDirectory = $resolvedJdkBin
}
if ($Packager -eq '') {
    $Packager = Join-Path $javaDirectory 'jpackage.exe'
}
foreach ($directory in @($dependencyDirectory, $inputDirectory, $classesDirectory, $outputDirectory)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$dependencies = @(
    @{ Path = 'org/json/json/20240303/json-20240303.jar'; Name = 'json.jar' },
    @{ Path = 'org/mp4parser/muxer/1.9.56/muxer-1.9.56.jar'; Name = 'muxer.jar' },
    @{ Path = 'org/mp4parser/isoparser/1.9.56/isoparser-1.9.56.jar'; Name = 'isoparser.jar' },
    @{ Path = 'org/aspectj/aspectjrt/1.9.7/aspectjrt-1.9.7.jar'; Name = 'aspectjrt.jar' },
    @{ Path = 'org/slf4j/slf4j-api/1.8.0-beta4/slf4j-api-1.8.0-beta4.jar'; Name = 'slf4j-api.jar' },
    @{ Path = 'org/slf4j/slf4j-nop/1.8.0-beta4/slf4j-nop-1.8.0-beta4.jar'; Name = 'slf4j-nop.jar' }
    @{ Path = 'com/formdev/flatlaf/3.6.2/flatlaf-3.6.2.jar'; Name = 'flatlaf.jar' }
    @{ Path = 'com/google/zxing/core/3.5.3/core-3.5.3.jar'; Name = 'zxing.jar' }
)
foreach ($dependency in $dependencies) {
    $target = Join-Path $dependencyDirectory $dependency.Name
    if (!(Test-Path -LiteralPath $target)) {
        Invoke-WebRequest -Uri ('https://repo.maven.apache.org/maven2/' + $dependency.Path) -OutFile $target
    }
    Copy-Item -LiteralPath $target -Destination $inputDirectory -Force
}
function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ('Windows build failed: ' + $Executable)
    }
}
$sharedNames = @('ShareLink', 'VideoInfo', 'HttpClient', 'NetworkPolicy', 'DouyinSigner', 'VideoResolver', 'BiliLogin')
$sources = @()
foreach ($name in $sharedNames) {
    $sources += Join-Path $projectDirectory ('src\cn\dousave\app\' + $name + '.java')
}
$imageMediaSource = Join-Path $projectDirectory 'src\cn\dousave\app\ImageMedia.java'
if (!(Test-Path -LiteralPath $imageMediaSource -PathType Leaf)) {
    throw 'The shared image format detector is missing.'
}
$sources += $imageMediaSource
$sources += Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
Invoke-Checked (Join-Path $javaDirectory 'javac.exe') (@('-encoding', 'UTF-8', '--release', '17', '-cp', ($dependencyDirectory + '\*'), '-d', $classesDirectory) + $sources)
Invoke-Checked (Join-Path $javaDirectory 'jar.exe') @('--create', '--file', (Join-Path $inputDirectory 'free-hd-download.jar'), '--main-class', 'cn.dousave.desktop.DesktopApp', '-C', $classesDirectory, '.', '-C', $projectDirectory, 'assets')
$runtime = Join-Path $workDirectory 'runtime'
if (!(Test-Path -LiteralPath $runtime)) {
    Invoke-Checked (Join-Path $javaDirectory 'jlink.exe') @('--add-modules', 'java.desktop,java.prefs,java.logging,jdk.crypto.ec,jdk.charsets', '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=2', '--output', $runtime)
}
$appDirectory = Join-Path $outputDirectory 'FreeHDDownload'
if (Test-Path -LiteralPath $appDirectory) {
    $resolvedApp = [IO.Path]::GetFullPath($appDirectory)
    if (!$resolvedApp.StartsWith([IO.Path]::GetFullPath($outputDirectory) + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove outside Windows output directory.'
    }
    Remove-Item -LiteralPath $resolvedApp -Recurse -Force
}
Invoke-Checked $Packager @('--type', 'app-image', '--name', 'FreeHDDownload', '--app-version', '1.2.4', '--vendor', 'Free HD Download contributors', '--input', $inputDirectory, '--main-jar', 'free-hd-download.jar', '--main-class', 'cn.dousave.desktop.DesktopApp', '--runtime-image', $runtime, '--dest', $outputDirectory, '--java-options', '-Dfile.encoding=UTF-8', '--temp', (Join-Path $workDirectory 'package-temp'))
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.txt') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Install-FreeHDDownload.ps1') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $projectDirectory 'LICENSE') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $projectDirectory 'NOTICE') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $projectDirectory 'assets\licenses') -Destination (Join-Path $appDirectory 'licenses') -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectDirectory 'assets\THIRD_PARTY_NOTICES.txt') -Destination $appDirectory
$archive = Join-Path $projectDirectory 'release\FreeHDDownload-1.2.4-windows-x64.zip'
Compress-Archive -Path $appDirectory -DestinationPath $archive -Force
Get-FileHash -LiteralPath $archive -Algorithm SHA256
Write-Output ('EXE: ' + (Join-Path $appDirectory 'FreeHDDownload.exe'))
if (!$KeepBuild) {
    $resolvedWork = [IO.Path]::GetFullPath($workDirectory)
    if (!$resolvedWork.StartsWith([IO.Path]::GetFullPath($projectDirectory) + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove outside project.'
    }
    Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}
