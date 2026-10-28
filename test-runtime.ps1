[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EclipseHome,
    [Parameter(Mandatory = $true)][string]$JdkHome,
    [string]$Label = 'eclipse',
    [string]$ReuseConfiguration,
    [string]$RuntimeBase,
    [switch]$KeepOpen,
    [switch]$OpeningOnly,
    [switch]$GutterOnly,
    [switch]$ScrollOnly,
    [switch]$ThemeOnly
)
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
if (!$RuntimeBase) { $RuntimeBase = Join-Path $repoRoot '.build' }
$pluginDir = Join-Path $EclipseHome 'plugins'
$runtimeRoot = Join-Path $RuntimeBase ('runtime-' + $Label + '-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss'))
$configuration = Join-Path $runtimeRoot 'configuration'
if ($ReuseConfiguration) { $configuration = [IO.Path]::GetFullPath($ReuseConfiguration) }
$workspace = Join-Path $runtimeRoot 'workspace'
$classes = Join-Path $runtimeRoot 'test-classes'
New-Item -ItemType Directory -Force -Path $configuration, $workspace, $classes | Out-Null
$platformClasspath = (Get-Content -LiteralPath (Join-Path $EclipseHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info') -Encoding UTF8 |
    Where-Object { $_ -match '^(org\.eclipse\.(core|equinox|osgi|jface|swt|ui|text|e4|jdt)|org\.osgi)' } |
    ForEach-Object {
        $location = ($_ -split ',')[2]
        $path = if ($location -match '^file:') { ([Uri]$location).LocalPath } else { Join-Path $EclipseHome $location }
        $path.Replace('\', '/')
    }) -join ';'
$mainJar = Join-Path $runtimeRoot 'dev.eclipseline_1.3.13.jar'
Copy-Item -LiteralPath (Join-Path $repoRoot 'dist\dev.eclipseline_1.3.13.jar') -Destination $mainJar
$argsForCompiler = @('--release', '11', '-encoding', 'UTF-8', '-d', ('"' + $classes.Replace('\', '/') + '"'),
    '-classpath', ('"' + $mainJar.Replace('\', '/') + ';' + $platformClasspath + '"'))
$argsForCompiler += Get-ChildItem -LiteralPath (Join-Path $repoRoot 'tests\plugin\src') -Filter '*.java' -Recurse |
    ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
$argFile = Join-Path $runtimeRoot 'test-javac.args'
[IO.File]::WriteAllLines($argFile, $argsForCompiler, [Text.UTF8Encoding]::new($false))
& (Join-Path $JdkHome 'bin\javac.exe') "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'Runtime test compilation failed.' }
$testJar = Join-Path $runtimeRoot 'dev.eclipseline.tests_1.0.0.jar'
$testPluginRoot = Join-Path $repoRoot 'tests\plugin'
& (Join-Path $JdkHome 'bin\jar.exe') --create --file $testJar --manifest (Join-Path $testPluginRoot 'META-INF\MANIFEST.MF') -C $classes . -C $testPluginRoot plugin.xml
if ($LASTEXITCODE -ne 0) { throw 'Runtime test packaging failed.' }
function ConvertTo-FileUri([string]$Path) { return [Uri]::new([IO.Path]::GetFullPath($Path)).AbsoluteUri }
$bundleLines = Get-Content -LiteralPath (Join-Path $EclipseHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info') -Encoding UTF8 |
    Where-Object { $_ -notmatch '^dev\.eclipseline(?:\.tests)?,' } |
    ForEach-Object {
        if ($_.StartsWith('#') -or !$_.Trim()) { $_ }
        else {
            $fields = $_ -split ','
            if (!($fields[2] -match '^[a-z]+:')) { $fields[2] = ConvertTo-FileUri (Join-Path $EclipseHome $fields[2]) }
            $fields -join ','
        }
    }
$bundleLines += 'dev.eclipseline,1.3.13,' + (ConvertTo-FileUri $mainJar) + ',4,false'
$bundleLines += 'dev.eclipseline.tests,1.0.0,' + (ConvertTo-FileUri $testJar) + ',4,false'
$bundleInfo = Join-Path $configuration 'bundles.info'
[IO.File]::WriteAllLines($bundleInfo, $bundleLines, [Text.UTF8Encoding]::new($false))
# Keep unrelated update, URI registration and repository startup jobs out of the isolated test.
$testSettingsDir = Join-Path $workspace '.metadata\.plugins\org.eclipse.core.runtime\.settings'
New-Item -ItemType Directory -Force -Path $testSettingsDir | Out-Null
$disabledStartups = ($bundleLines | Where-Object { $_ -and !$_.StartsWith('#') -and !$_.StartsWith('dev.eclipseline,') -and !$_.StartsWith('dev.eclipseline.tests,') } | ForEach-Object { ($_ -split ',')[0] }) -join ';'
[IO.File]::WriteAllLines((Join-Path $testSettingsDir 'org.eclipse.ui.workbench.prefs'), @('eclipse.preferences.version=1', ('PLUGINS_NOT_ACTIVATED_ON_STARTUP=' + $disabledStartups)), [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllLines((Join-Path $testSettingsDir 'org.eclipse.ui.prefs'), @('eclipse.preferences.version=1', ('PLUGINS_NOT_ACTIVATED_ON_STARTUP=' + $disabledStartups)), [Text.UTF8Encoding]::new($false))
$framework = Get-ChildItem -LiteralPath $pluginDir -Filter 'org.eclipse.osgi_*.jar' | Select-Object -First 1
$configurator = Get-ChildItem -LiteralPath $pluginDir -Filter 'org.eclipse.equinox.simpleconfigurator_*.jar' | Select-Object -First 1
$configLines = @(
    ('osgi.framework=' + (ConvertTo-FileUri $framework.FullName)),
    ('osgi.bundles=reference:' + (ConvertTo-FileUri $configurator.FullName) + '@1:start'),
    ('org.eclipse.equinox.simpleconfigurator.configUrl=' + (ConvertTo-FileUri $bundleInfo)),
    'osgi.bundles.defaultStartLevel=4',
    'eclipse.application=org.eclipse.ui.ide.workbench',
    'eclipse.product=org.eclipse.platform.ide',
    'org.eclipse.update.reconcile=false',
    'osgi.configuration.cascaded=false',
    'osgi.framework.extensions='
    'osgi.checkConfiguration=true'
)
[IO.File]::WriteAllLines((Join-Path $configuration 'config.ini'), $configLines, [Text.UTF8Encoding]::new($false))
$launcher = Get-ChildItem -LiteralPath $pluginDir -Filter 'org.eclipse.equinox.launcher_*.jar' | Select-Object -First 1
$resultFile = Join-Path $runtimeRoot 'results.txt'
Write-Output "Test workspace: $workspace"
Write-Output "Result file: $resultFile"
& (Join-Path $JdkHome 'bin\java.exe') '-Dnotebook.test.realWorkbench=true' "-Dnotebook.test.scrollOnly=$($ScrollOnly.IsPresent.ToString().ToLowerInvariant())" "-Dnotebook.test.gutterOnly=$($GutterOnly.IsPresent.ToString().ToLowerInvariant())" "-Dnotebook.test.themeOnly=$($ThemeOnly.IsPresent.ToString().ToLowerInvariant())" "-Dnotebook.test.openingOnly=$($OpeningOnly.IsPresent.ToString().ToLowerInvariant())" "-Dnotebook.test.result=$resultFile" "-Dnotebook.test.keepOpen=$($KeepOpen.IsPresent.ToString().ToLowerInvariant())" -Xmx1536m -jar $launcher.FullName -install $EclipseHome -configuration $configuration -data $workspace -application org.eclipse.ui.ide.workbench -clean -consoleLog -nosplash
$runtimeExit = $LASTEXITCODE
if (Test-Path -LiteralPath $resultFile) {
    $testResultLines = Get-Content -LiteralPath $resultFile -Encoding UTF8
    $testResultLines
    if (!($testResultLines -match '^PASS:') -or ($testResultLines -match '^FAIL:')) { $runtimeExit = 1 }
} else { $runtimeExit = 1 }
if ($runtimeExit -ne 0) { throw "Runtime tests failed with exit code $runtimeExit. See $runtimeRoot" }
