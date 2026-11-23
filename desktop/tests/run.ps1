param([switch]$Online, [string]$JdkBin = '')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$jdk = Get-ChildItem -LiteralPath (Join-Path $projectDirectory '.tools\desktop\jdk') -Directory | Select-Object -First 1
$javaDirectory = Join-Path $jdk.FullName 'bin'
if (!$JdkBin.Equals('')) {
    $resolvedJdkBin = [IO.Path]::GetFullPath($JdkBin)
    foreach ($tool in @('javac.exe', 'java.exe')) {
        if (!(Test-Path -LiteralPath (Join-Path $resolvedJdkBin $tool))) {
            throw ('Requested JDK tool is missing: ' + $tool)
        }
    }
    $javaDirectory = $resolvedJdkBin
}
$productionClasses = Join-Path $projectDirectory 'build\windows\production-classes'
$classes = Join-Path $projectDirectory 'build\windows\test-classes'
$libraries = Join-Path $projectDirectory '.tools\desktop\lib\*'
$classpath = $classes + ';' + $productionClasses + ';' + $libraries
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object { $_.FullName }
& (Join-Path $javaDirectory 'javac.exe') -encoding UTF-8 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) {
    throw 'Desktop test compilation failed.'
}
& (Join-Path $javaDirectory 'java.exe') -cp $classpath cn.dousave.desktop.BiliQrTest
if ($LASTEXITCODE -ne 0) {
    throw 'Bilibili QR round-trip check failed.'
}
if (!$Online) {
    Write-Output 'Tests compiled. Use -Online to run real public-link downloads and desktop UI checks.'
    exit 0
}
$buildDirectory = Join-Path $projectDirectory 'build'
$runName = 'desktop-test-run-' + [guid]::NewGuid().ToString('N')
$testData = Join-Path $buildDirectory $runName
try {
    New-Item -ItemType Directory -Path $testData -Force | Out-Null
    $runtimeOptions = @('-Dfile.encoding=UTF-8', ('-Dfreehd.dataDirectory=' + $testData), ('-Djava.io.tmpdir=' + $testData), '-cp', $classpath)
    & (Join-Path $javaDirectory 'java.exe') @runtimeOptions cn.dousave.desktop.DesktopTest (Join-Path $testData 'media') (Join-Path $testData 'empty-window.png')
    if ($LASTEXITCODE -ne 0) {
        throw 'Desktop media checks failed.'
    }
    & (Join-Path $javaDirectory 'java.exe') @runtimeOptions cn.dousave.desktop.DesktopUiTest (Join-Path $testData 'parsed-window.png')
    if ($LASTEXITCODE -ne 0) {
        throw 'Desktop UI checks failed.'
    }
    Write-Output 'Desktop media and UI checks passed.'
} finally {
    $resolvedBuild = [IO.Path]::GetFullPath($buildDirectory)
    $resolvedTestData = [IO.Path]::GetFullPath($testData)
    if (!$resolvedTestData.StartsWith($resolvedBuild + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove desktop test data outside the build directory.'
    }
    if (Test-Path -LiteralPath $resolvedTestData) {
        if (@(Get-ChildItem -LiteralPath $resolvedTestData -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -gt 0) {
            throw 'Refusing to remove test data containing a reparse point.'
        }
        Remove-Item -LiteralPath $resolvedTestData -Recurse -Force
    }
}
