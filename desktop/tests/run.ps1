param([switch]$Online)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$jdk = Get-ChildItem -LiteralPath (Join-Path $projectDirectory '.tools\desktop\jdk') -Directory | Select-Object -First 1
$javaDirectory = Join-Path $jdk.FullName 'bin'
$productionClasses = Join-Path $projectDirectory 'build\windows\production-classes'
$classes = Join-Path $projectDirectory 'build\windows\test-classes'
$libraries = Join-Path $projectDirectory '.tools\desktop\lib\*'
$classpath = $classes + ';' + $productionClasses + ';' + $libraries
$testData = Join-Path $projectDirectory 'build\desktop-test-data'
New-Item -ItemType Directory -Path $testData -Force | Out-Null
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object { $_.FullName }
& (Join-Path $javaDirectory 'javac.exe') -encoding UTF-8 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) {
    throw 'Desktop test compilation failed.'
}
if (!$Online) {
    Write-Output 'Tests compiled. Use -Online to run real public-link downloads and desktop UI checks.'
    exit 0
}
$runtimeOptions = @('-Dfile.encoding=UTF-8', ('-Dfreehd.dataDirectory=' + $testData), ('-Djava.io.tmpdir=' + $testData), '-cp', $classpath)
& (Join-Path $javaDirectory 'java.exe') @runtimeOptions cn.dousave.desktop.DesktopTest (Join-Path $testData 'media') (Join-Path $testData 'empty-window.png')
if ($LASTEXITCODE -ne 0) {
    throw 'Desktop media checks failed.'
}
& (Join-Path $javaDirectory 'java.exe') @runtimeOptions cn.dousave.desktop.DesktopUiTest (Join-Path $testData 'parsed-window.png')
if ($LASTEXITCODE -ne 0) {
    throw 'Desktop UI checks failed.'
}
Write-Output ('Test artifacts: ' + $testData)
