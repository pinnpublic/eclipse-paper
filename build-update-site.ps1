[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EclipseHome,
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
$javaTool = if ($JdkHome) { Join-Path $JdkHome 'bin\java.exe' } else { (Get-Command java).Source }
$jarTool = Join-Path (Split-Path $javaTool) 'jar.exe'
$plugins = Join-Path $EclipseHome 'plugins'
$mainJar = Join-Path $repoRoot 'dist\dev.eclipseline_1.3.13.jar'
if (!(Test-Path -LiteralPath $mainJar)) { throw 'Run build.ps1 first.' }
$work = Join-Path ([IO.Path]::GetTempPath()) ('eclipse-notebook-p2-' + [Guid]::NewGuid().ToString('N'))
$stage = Join-Path $work 'stage'
$repository = Join-Path $work 'repository'
$configuration = Join-Path $work 'configuration'
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'plugins'), (Join-Path $stage 'features'), $repository, $configuration | Out-Null
Copy-Item -LiteralPath $mainJar -Destination (Join-Path $stage 'plugins')
$featureJar = Join-Path $stage 'features\dev.eclipseline.feature_1.3.13.jar'
& $jarTool --create --file $featureJar -C (Join-Path $repoRoot 'feature') feature.xml
if ($LASTEXITCODE -ne 0) { throw 'Feature packaging failed.' }

function ConvertTo-FileUri([string]$Path) { [Uri]::new([IO.Path]::GetFullPath($Path)).AbsoluteUri }
$bundleInfo = Join-Path $configuration 'bundles.info'
$bundles = Get-Content -LiteralPath (Join-Path $EclipseHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info') -Encoding UTF8 |
    ForEach-Object {
        if ($_.StartsWith('#') -or !$_.Trim()) { $_ }
        else {
            $fields = $_ -split ','
            if (!($fields[2] -match '^[a-z]+:')) { $fields[2] = ConvertTo-FileUri (Join-Path $EclipseHome $fields[2]) }
            $fields -join ','
        }
    }
[IO.File]::WriteAllLines($bundleInfo, $bundles, [Text.UTF8Encoding]::new($false))
$framework = Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.osgi_*.jar' | Select-Object -First 1
$configurator = Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.equinox.simpleconfigurator_*.jar' | Select-Object -First 1
$configLines = @(
    ('osgi.framework=' + (ConvertTo-FileUri $framework.FullName)),
    ('osgi.bundles=reference:' + (ConvertTo-FileUri $configurator.FullName) + '@1:start'),
    ('org.eclipse.equinox.simpleconfigurator.configUrl=' + (ConvertTo-FileUri $bundleInfo)),
    'osgi.bundles.defaultStartLevel=4',
    'org.eclipse.update.reconcile=false',
    'osgi.configuration.cascaded=false',
    'osgi.framework.extensions='
)
[IO.File]::WriteAllLines((Join-Path $configuration 'config.ini'), $configLines, [Text.UTF8Encoding]::new($false))
$launcher = Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.equinox.launcher_*.jar' | Select-Object -First 1
$launcherArgs = @('-jar', $launcher.FullName, '-install', $EclipseHome,
    '-configuration', $configuration, '-nosplash', '-consoleLog')
$repositoryUri = ConvertTo-FileUri $repository
& $javaTool @launcherArgs -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher -metadataRepository $repositoryUri -metadataRepositoryName 'Paper Update Site' -artifactRepository $repositoryUri -artifactRepositoryName 'Paper Artifacts' -source $stage -publishArtifacts -compress
if ($LASTEXITCODE -ne 0) { throw 'p2 feature/bundle publishing failed.' }
& $javaTool @launcherArgs -application org.eclipse.equinox.p2.publisher.CategoryPublisher -metadataRepository $repositoryUri -categoryDefinition (ConvertTo-FileUri (Join-Path $repoRoot 'feature\category.xml')) -categoryQualifier dev.eclipseline.category -compress
if ($LASTEXITCODE -ne 0) { throw 'p2 category publishing failed.' }
Copy-Item -LiteralPath (Join-Path $repoRoot 'feature\category.xml') -Destination (Join-Path $repository 'category.xml')
$zipPath = Join-Path $repoRoot 'dist\paper-update-site-1.3.13.zip'
# Windows PowerShell Compress-Archive writes backslashes into ZIP member names.
# p2 resolves artifact URLs with '/', so package with the JDK ZIP/JAR writer.
& $jarTool --create --no-manifest --file $zipPath -C $repository .
if ($LASTEXITCODE -ne 0) { throw 'Update-site ZIP packaging failed.' }
$entries = & $jarTool --list --file $zipPath
foreach ($entry in @('plugins/dev.eclipseline_1.3.13.jar', 'features/dev.eclipseline.feature_1.3.13.jar')) {
    if ($entries -cnotcontains $entry) { throw "Missing exact ZIP entry: $entry" }
}
if ($entries | Where-Object { $_.Contains('\') }) { throw 'ZIP member names must use forward slashes.' }

# Listing validates metadata only. Also collect both actual artifacts via p2.
$archiveUri = 'jar:' + (ConvertTo-FileUri $zipPath) + '!/'
$previousErrorPreference = $ErrorActionPreference
try {
    # Windows PowerShell wraps harmless Java stderr warnings as error records.
    # Preserve them, and use the director exit code and feature listing for validation.
    $ErrorActionPreference = 'Continue'
    $listOutput = & $javaTool @launcherArgs -application org.eclipse.equinox.p2.director -repository $archiveUri -list 2>&1
    $listExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousErrorPreference }
$listOutput | ForEach-Object { Write-Output $_ }
if ($listExit -ne 0 -or !(($listOutput -join "`n") -match 'dev\.eclipseline\.feature\.feature\.group')) {
    throw 'p2 could not read the installable feature from the finished ZIP.'
}
$mirror = Join-Path $work 'collected-artifacts'
& $javaTool @launcherArgs -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication -source $archiveUri -destination (ConvertTo-FileUri $mirror) -verbose
if ($LASTEXITCODE -ne 0) { throw 'p2 could not collect the artifacts from the finished ZIP.' }
foreach ($entry in @('plugins/dev.eclipseline_1.3.13.jar', 'features/dev.eclipseline.feature_1.3.13.jar')) {
    $collected = Join-Path $mirror $entry
    if (!(Test-Path -LiteralPath $collected)) { throw "p2 did not collect: $entry" }
    if ((Get-FileHash -LiteralPath $collected -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath (Join-Path $repository $entry) -Algorithm SHA256).Hash) {
        throw "Collected artifact differs from its source: $entry"
    }
}
$zipHash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$zipPath.sha256", "$zipHash  $([IO.Path]::GetFileName($zipPath))`n", [Text.UTF8Encoding]::new($false))
$verificationLines = @(
    ('Verified: ' + [DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss')),
    ('Eclipse: ' + $EclipseHome),
    ('Java: ' + $javaTool),
    'PASS: exact forward-slash ZIP artifact entries',
    'PASS: p2 director reads the installable feature',
    'PASS: p2 mirror collects both plug-in and feature artifacts',
    'PASS: both collected artifact SHA-256 values equal repository originals',
    ('ZIP SHA-256: ' + $zipHash)
)
[IO.File]::WriteAllLines((Join-Path $repoRoot 'dist\p2-verification.txt'), $verificationLines, [Text.UTF8Encoding]::new($false))
Write-Output "Built and verified $zipPath"
& (Join-Path $repoRoot 'prepare-update-site.ps1')
