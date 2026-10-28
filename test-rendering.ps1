[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EclipseHome,
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
$compiler = if ($JdkHome) { Join-Path $JdkHome 'bin\javac.exe' } else { (Get-Command javac).Source }
$java = Join-Path (Split-Path $compiler) 'java.exe'
$mainJar = Join-Path $repoRoot 'dist\dev.eclipseline_1.3.12.jar'
$classes = Join-Path $repoRoot '.build\rendering-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$platform = (Get-ChildItem -LiteralPath (Join-Path $EclipseHome 'plugins') -Filter '*.jar' |
    Where-Object { $_.Name -match '^(org\.eclipse\.(core|equinox|osgi|jface|swt|ui|text|e4)|org\.osgi)' } |
    ForEach-Object FullName) -join ';'
$classpath = $mainJar + ';' + $platform
$testSources = (Get-ChildItem -LiteralPath (Join-Path $repoRoot 'tests') -Filter '*Test.java').FullName
& $compiler --release 11 -encoding UTF-8 -classpath $classpath -d $classes @testSources
if ($LASTEXITCODE -ne 0) { throw 'Focused rendering test compilation failed.' }
$result = @(& $java '-Djava.awt.headless=true' -classpath ($classes + ';' + $classpath) HighlightTransparencyTest)
if ($LASTEXITCODE -ne 0) { throw 'Focused rendering tests failed.' }
$catalogResult = & $java '-Djava.awt.headless=true' -classpath ($classes + ';' + $classpath) TextureCatalogTest (Join-Path $classes 'paper-previews')
if ($LASTEXITCODE -ne 0) { throw 'Paper catalog tests failed.' }
$result += $catalogResult
$darkResult = & $java '-Djava.awt.headless=true' -classpath ($classes + ';' + $classpath) DarkPaperTest (Join-Path $classes 'dark-paper-previews')
if ($LASTEXITCODE -ne 0) { throw 'Dark paper profile tests failed.' }
$result += $darkResult
$frameResult = & $java '-Djava.awt.headless=true' -classpath ($classes + ';' + $classpath) dev.eclipseline.NotebookFrameTest
if ($LASTEXITCODE -ne 0) { throw 'Notebook frame tests failed.' }
$result += $frameResult
$result
[IO.File]::WriteAllLines((Join-Path $repoRoot 'dist\rendering-verification.txt'), @($result), [Text.UTF8Encoding]::new($false))
