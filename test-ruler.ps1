[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EclipseHome,
    [Parameter(Mandatory = $true)][string]$JdkHome
)
$ErrorActionPreference = 'Stop'
$classes = Join-Path $PSScriptRoot '.build\ruler-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$classpath = (Join-Path $PSScriptRoot 'dist\dev.eclipseline_1.3.5.jar') + ';' + (Join-Path $EclipseHome 'plugins\*')
& (Join-Path $JdkHome 'bin\javac.exe') --release 11 -encoding UTF-8 -classpath $classpath -d $classes (Join-Path $PSScriptRoot 'tests\RulerSmokeTest.java') (Join-Path $PSScriptRoot 'tests\StabilityAuditTest.java') (Join-Path $PSScriptRoot 'tests\ReviewRegressionTest.java') (Join-Path $PSScriptRoot 'tests\RulerMatteTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Ruler regression test compilation failed.' }
$result = @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.RulerSmokeTest (Join-Path $classes 'ruler.png'))
if ($LASTEXITCODE -ne 0) { throw 'Ruler regression tests failed.' }
$result += @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.StabilityAuditTest)
if ($LASTEXITCODE -ne 0) { throw 'Stability audit tests failed.' }
$result += @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.ReviewRegressionTest)
if ($LASTEXITCODE -ne 0) { throw 'Review regression tests failed.' }
$result += @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.RulerMatteTest (Join-Path $classes 'matte-comparison'))
if ($LASTEXITCODE -ne 0) { throw 'Ruler edge blending tests failed.' }
$result
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'dist\ruler-verification.txt'), $result, [Text.UTF8Encoding]::new($false))
