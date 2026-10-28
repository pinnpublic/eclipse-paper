[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EclipseHome,
    [Parameter(Mandatory = $true)][string]$JdkHome
)
$ErrorActionPreference = 'Stop'
$classes = Join-Path $PSScriptRoot '.build\ruler-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$classpath = (Join-Path $PSScriptRoot 'dist\dev.eclipseline_1.3.13.jar') + ';' + (Join-Path $EclipseHome 'plugins\*')
& (Join-Path $JdkHome 'bin\javac.exe') --release 11 -encoding UTF-8 -classpath $classpath -d $classes (Join-Path $PSScriptRoot 'tests\RulerPaintIsolationTest.java') (Join-Path $PSScriptRoot 'tests\ScrollResourceTest.java') (Join-Path $PSScriptRoot 'tests\RendererKeyTest.java') (Join-Path $PSScriptRoot 'tests\SynchronousScrollTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Paint ordering test compilation failed.' }
& (Join-Path $JdkHome 'bin\javac.exe') --release 11 -encoding UTF-8 -classpath $classpath -d $classes (Join-Path $PSScriptRoot 'tests\RulerSmokeTest.java') (Join-Path $PSScriptRoot 'tests\StabilityAuditTest.java') (Join-Path $PSScriptRoot 'tests\ReviewRegressionTest.java') (Join-Path $PSScriptRoot 'tests\RulerMatteTest.java') (Join-Path $PSScriptRoot 'tests\RulerModelTest.java') (Join-Path $PSScriptRoot 'tests\ScrollAlignmentTest.java')
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
$modelResult = @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.RulerModelTest)
if ($LASTEXITCODE -ne 0) { throw 'Clean ruler model tests failed.' }
$modelResult
$result += $modelResult
$isolationResult = @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.RulerPaintIsolationTest)
if ($LASTEXITCODE -ne 0) { throw 'Paint ordering regression failed.' }
$isolationResult
$result += $isolationResult
$keyResult = @(& (Join-Path $JdkHome 'bin\java.exe') '-Djava.awt.headless=true' -classpath ($classes + ';' + $classpath) dev.eclipseline.RendererKeyTest)
if ($LASTEXITCODE -ne 0) { throw 'Renderer cache key equivalence failed.' }
$keyResult
$result += $keyResult
$resourceResult = @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.ScrollResourceTest)
if ($LASTEXITCODE -ne 0) { throw 'Scroll raster reuse regression failed.' }
$resourceResult
$result += $resourceResult
$scrollResult = @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.ScrollAlignmentTest (Join-Path $classes 'wheel'))
if ($LASTEXITCODE -ne 0) { throw 'Native wheel alignment tests failed.' }
$scrollResult
$result += $scrollResult
$synchronousResult = @(& (Join-Path $JdkHome 'bin\java.exe') -classpath ($classes + ';' + $classpath) dev.eclipseline.SynchronousScrollTest)
if ($LASTEXITCODE -ne 0) { throw 'Synchronous source/gutter paint regression failed.' }
$synchronousResult
$result += $synchronousResult
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'dist\ruler-verification.txt'), $result, [Text.UTF8Encoding]::new($false))
