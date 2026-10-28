[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EclipseHome,
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
$pluginDir = Join-Path $EclipseHome 'plugins'
if (!(Test-Path -LiteralPath $pluginDir)) { throw "Eclipse plugins folder not found: $pluginDir" }
$javaCompiler = if ($JdkHome) { Join-Path $JdkHome 'bin\javac.exe' } else { (Get-Command javac).Source }
$jarTool = Join-Path (Split-Path $javaCompiler) 'jar.exe'
$classes = Join-Path $repoRoot ('.build\classes-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmssfff'))
$output = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $classes, $output | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Filter '*.java' -Recurse
$platformClasspath = (Get-ChildItem -LiteralPath $pluginDir -Filter '*.jar' |
    Where-Object { $_.Name -match '^(org\.eclipse\.(core|equinox|osgi|jface|swt|ui|text|e4)|org\.osgi)' } |
    ForEach-Object { $_.FullName.Replace('\', '/') }) -join ';'
$compilerArgs = @('--release', '11', '-encoding', 'UTF-8', '-d', ('"' + $classes.Replace('\', '/') + '"'),
    '-classpath', ('"' + $platformClasspath + '"'))
$compilerArgs += $sources.FullName | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
$argFile = Join-Path $repoRoot '.build\javac.args'
[IO.File]::WriteAllLines($argFile, $compilerArgs, [Text.UTF8Encoding]::new($false))
& $javaCompiler "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$jarPath = Join-Path $output 'dev.eclipseline_1.3.13.jar'
& $jarTool --create --file $jarPath --manifest (Join-Path $repoRoot 'META-INF\MANIFEST.MF') -C $classes . -C $repoRoot plugin.xml -C $repoRoot assets -C $repoRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Plug-in packaging failed.' }
$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$jarPath.sha256", "$hash  dev.eclipseline_1.3.13.jar`n", [Text.UTF8Encoding]::new($false))
Write-Output "Built $jarPath (Java 11 bytecode)"
