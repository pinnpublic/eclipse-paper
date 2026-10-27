[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repoRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$manifest = Get-Content -LiteralPath (Join-Path $repoRoot 'META-INF\MANIFEST.MF') -Encoding UTF8
$version = (($manifest | Where-Object { $_ -match '^Bundle-Version:' }) -split ':', 2)[1].Trim()
$archive = Join-Path $repoRoot "dist\paper-update-site-$version.zip"
$destination = [IO.Path]::GetFullPath((Join-Path $repoRoot 'docs\updates'))
if (!$destination.StartsWith($repoRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Update site must stay inside the repository.' }
foreach ($folder in @((Join-Path $repoRoot 'docs'), $destination)) {
    if ((Test-Path -LiteralPath $folder) -and ((Get-Item -LiteralPath $folder).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Update site cannot use a reparse point: $folder"
    }
}
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    foreach ($required in @('content.jar', 'artifacts.jar', "plugins/dev.eclipseline_$version.jar", "features/dev.eclipseline.feature_$version.jar")) {
        if (!$zip.GetEntry($required)) { throw "Update site is missing $required" }
    }
    foreach ($entry in $zip.Entries) {
        $target = [IO.Path]::GetFullPath((Join-Path $destination $entry.FullName))
        if (!$target.StartsWith($destination + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe ZIP path: $($entry.FullName)" }
        if ($entry.FullName.EndsWith('/')) { New-Item -ItemType Directory -Force -Path $target | Out-Null; continue }
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        $inputStream = $entry.Open()
        try {
            $outputStream = [IO.File]::Create($target)
            try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose() }
        } finally { $inputStream.Dispose() }
    }
} finally { $zip.Dispose() }
foreach ($name in @('content', 'artifacts')) {
    $metadata = [IO.Compression.ZipFile]::OpenRead((Join-Path $destination "$name.jar"))
    try {
        $reader = [IO.StreamReader]::new($metadata.GetEntry("$name.xml").Open(), [Text.Encoding]::UTF8)
        try { $xml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($xml -match '(?i)file:/|(?<![A-Z])[A-Z]:[\\/]') { throw "Local filesystem reference in public $name metadata. Rebuild with explicit repository names." }
    } finally { $metadata.Dispose() }
}
$plugin = "dev.eclipseline_$version.jar"
if ((Get-FileHash -LiteralPath (Join-Path $destination "plugins\$plugin") -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath (Join-Path $repoRoot "dist\$plugin") -Algorithm SHA256).Hash) { throw 'Published plug-in differs from the built plug-in.' }
Write-Output "Prepared GitHub Pages p2 repository: $destination"
