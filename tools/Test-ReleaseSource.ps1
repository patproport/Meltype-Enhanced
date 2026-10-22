[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ArchivePath,
    [Parameter(Mandatory)][string]$ApkPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Fail([string]$Reason) { throw $Reason }
$archive = (Resolve-Path -LiteralPath $ArchivePath).Path
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$sidecar = "$archive.sha256"
if (-not (Test-Path -LiteralPath $sidecar -PathType Leaf)) { Fail 'Missing archive SHA-256 sidecar.' }
$expected = @{}
foreach ($line in Get-Content -LiteralPath $sidecar) {
    if ($line -notmatch '^([0-9a-fA-F]{64})  ([^\\/]+)$') { Fail 'Invalid SHA-256 sidecar format.' }
    $expected[$Matches[2]] = $Matches[1].ToLowerInvariant()
}
foreach ($file in @($archive, $apk)) {
    $name = [IO.Path]::GetFileName($file)
    if (-not $expected.ContainsKey($name)) { Fail "Missing SHA-256 for $name" }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected[$name]) {
        Fail "SHA-256 mismatch for $name"
    }
}
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('meltype-source-check-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    $entries = @(& tar -tzf $archive)
    if ($LASTEXITCODE -ne 0) { Fail 'Cannot list source archive.' }
    if ($entries.Count -eq 0 -or @($entries | Where-Object { $_ -match '^/|(^|/)\.\.(?:/|$)|^[A-Za-z]:|\\' }).Count -gt 0) {
        Fail 'Archive contains an unsafe path.'
    }
    & tar -xzf $archive -C $scratch
    if ($LASTEXITCODE -ne 0) { Fail 'Cannot extract source archive.' }
    $roots = @(Get-ChildItem -LiteralPath $scratch -Directory)
    if ($roots.Count -ne 1) { Fail 'Expected one source root.' }
    $root = $roots[0].FullName
    $manifestPath = Join-Path $root 'RELEASE-MANIFEST.json'
    $hashesPath = Join-Path $root 'SOURCE-FILES.sha256'
    if (-not (Test-Path -LiteralPath $manifestPath) -or -not (Test-Path -LiteralPath $hashesPath)) { Fail 'Missing release manifest or file hash list.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.schema -ne 1 -or $manifest.tag -notmatch '^v[0-9]+\.[0-9]+\.[0-9]+$' -or
        $manifest.repositoryCommit -notmatch '^[0-9a-f]{40}$' -or
        $manifest.upstreamCommit -notmatch '^[0-9a-f]{40}$' -or
        $manifest.mozcCommit -notmatch '^[0-9a-f]{40}$' -or
        $manifest.dotnetRuntimeCommit -notmatch '^[0-9a-f]{40}$') { Fail 'Invalid release manifest revisions.' }
    if ($manifest.apkFile -ne [IO.Path]::GetFileName($apk) -or $manifest.apkSha256 -ne $expected[$manifest.apkFile]) {
        Fail 'APK does not match release manifest.'
    }
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $apkZip = [IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($name in @('libMeltypeNative.so', 'libmozc_helper.so', 'libmozc_data.so')) {
            $entry = $apkZip.GetEntry("lib/arm64-v8a/$name")
            if ($null -eq $entry) { Fail "Missing APK native library: $name" }
            $stream = $entry.Open()
            $sha = [Security.Cryptography.SHA256]::Create()
            try { $actual = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
            finally { $stream.Dispose(); $sha.Dispose() }
            if ($manifest.nativeSha256.$name -ne $actual) { Fail "APK native SHA-256 mismatch: $name" }
        }
    } finally { $apkZip.Dispose() }
    if ((Get-FileHash -LiteralPath $hashesPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sourceHashListSha256) {
        Fail 'Source hash list does not match release manifest.'
    }
    $recorded = @{}
    foreach ($line in Get-Content -LiteralPath $hashesPath) {
        if ($line -notmatch '^([0-9a-f]{64})  ([^\\]+)$') { Fail 'Invalid source hash entry.' }
        $name = $Matches[2]
        if ($name -match '(^|/)\.\.(/|$)|^/|^[A-Za-z]:') { Fail 'Unsafe source hash path.' }
        if ($recorded.ContainsKey($name)) { Fail 'Duplicate source hash entry.' }
        $recorded[$name] = $Matches[1]
    }
    $files = @(Get-ChildItem -LiteralPath $root -File -Recurse -Force |
        Where-Object { $_.FullName -ne $manifestPath -and $_.FullName -ne $hashesPath })
    if ($files.Count -ne $recorded.Count) { Fail 'Source file count differs from hash list.' }
    foreach ($file in $files) {
        $name = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
        if (-not $recorded.ContainsKey($name)) { Fail "Unlisted source file: $name" }
        if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $recorded[$name]) {
            Fail "Source SHA-256 mismatch: $name"
        }
        $localSource = $name -notmatch '^(?:upstream|external/mozc|external/dotnet-runtime)/'
        if ($name -match '^(?:build|app/build|release)/|(^|/)(?:\.git|\.gradle|\.cxx)(/|$)' -or
            ($localSource -and ($name -match '(^|/)(?:local\.properties|\.env(?:\..*)?)(/|$)' -or
            $name -match '(?i)\.(?:jks|keystore|p12|pfx|pem|apk|aab)$'))) { Fail "Forbidden source file: $name" }
    }
    $mozcRef = (Get-Content -LiteralPath (Join-Path $root 'upstream/native/mozc/MOZC_COMMIT') -Raw).Trim()
    if ($mozcRef -ne $manifest.mozcCommit) { Fail 'Mozc revision mismatch in source tree.' }
    $archiveMozcRef = (Get-Content -LiteralPath (Join-Path $root 'external/mozc/MOZC_SOURCE_REVISION') -Raw).Trim()
    if ($archiveMozcRef -ne $manifest.mozcCommit) { Fail 'Mozc archive revision marker mismatch.' }
    foreach ($path in @('LICENSE', 'THIRD-PARTY-NOTICES.md', 'licenses/KOTLIN-NOTICES.md',
        'licenses/native/MOZC-LICENSE.txt', 'licenses/native/MOZC-CREDITS.html',
        'licenses/native/DOTNET-LICENSE.txt', 'licenses/native/DOTNET-THIRD-PARTY-NOTICES.txt',
        'upstream/THIRD-PARTY-NOTICES.md', 'upstream/dictionaries/japanese.txt',
        'external/mozc/LICENSE', 'external/mozc/src/MODULE.bazel',
        'external/dotnet-runtime/src/libraries/System.Private.CoreLib',
        'external/mozc/src/data/dictionary_oss/README.txt',
        'external/mozc-bazel-vendor/_registries')) {
        if (-not (Test-Path -LiteralPath (Join-Path $root $path))) { Fail "Missing source or notice: $path" }
    }
    Write-Host "Verified $($files.Count) source files for $($manifest.tag)"
    Write-Host "Public commit: $($manifest.repositoryCommit)"
    Write-Host "Upstream: $($manifest.upstreamCommit)"
    Write-Host "Mozc: $($manifest.mozcCommit)"
    Write-Host ".NET runtime: $($manifest.dotnetRuntimeCommit)"
    Write-Host "APK SHA-256: $($manifest.apkSha256)"
} finally {
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($scratch.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $scratch -Leaf) -like 'meltype-source-check-*' -and
        (Test-Path -LiteralPath $scratch)) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
