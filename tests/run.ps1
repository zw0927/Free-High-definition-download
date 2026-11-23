$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$javaDirectory = 'C:\Program Files\Java\jdk-17\bin'
$testDirectory = Join-Path $projectDirectory 'build\tests'
$dependency = Join-Path $projectDirectory '.tools\json.jar'
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $dependency) -Force | Out-Null
if (!(Test-Path -LiteralPath $dependency)) {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $dependency
}
$sources = @(
    (Join-Path $projectDirectory 'src\cn\dousave\app\ShareLink.java'),
    (Join-Path $projectDirectory 'src\cn\dousave\app\VideoInfo.java'),
    (Join-Path $projectDirectory 'src\cn\dousave\app\HttpClient.java'),
    (Join-Path $projectDirectory 'src\cn\dousave\app\NetworkPolicy.java'),
    (Join-Path $projectDirectory 'src\cn\dousave\app\DouyinSigner.java'),
    (Join-Path $projectDirectory 'src\cn\dousave\app\VideoResolver.java'),
    (Join-Path $projectDirectory 'src\cn\dousave\app\BiliLogin.java'),
    (Join-Path $PSScriptRoot 'CoreTest.java'),
    (Join-Path $PSScriptRoot 'ResolveProbe.java')
)
$sources += Join-Path $projectDirectory 'src\cn\dousave\app\ImageMedia.java'
& (Join-Path $javaDirectory 'javac.exe') -encoding UTF-8 -cp $dependency -d $testDirectory @sources
if ($LASTEXITCODE -ne 0) {
    throw 'Core test compilation failed.'
}
& (Join-Path $javaDirectory 'java.exe') '-Dfile.encoding=UTF-8' -cp ($testDirectory + ';' + $dependency) cn.dousave.app.CoreTest (Join-Path $PSScriptRoot 'fixture.json')
if ($LASTEXITCODE -ne 0) {
    throw 'Core checks failed.'
}
$resolvedTests = [System.IO.Path]::GetFullPath($testDirectory)
$resolvedProject = [System.IO.Path]::GetFullPath($projectDirectory) + [System.IO.Path]::DirectorySeparatorChar
if (!$resolvedTests.StartsWith($resolvedProject, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to remove files outside this project.'
}
Remove-Item -LiteralPath $resolvedTests -Recurse -Force
