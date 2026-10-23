# Optional full offline source mirror. This copies and hashes the full .NET and
# Bazel vendor trees and can take hours; it is not the v0.1.0 publication gate.
# See docs/RELEASE-SOURCE.md for the repository plus fixed-source-link method.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+$')][string]$Tag,
    [Parameter(Mandatory)][string]$ApkPath,
    [Parameter(Mandatory)][string]$MozcCheckout,
    [Parameter(Mandatory)][string]$MozcVendorDirectory,
    [Parameter(Mandatory)][string]$DotNetRuntimeCheckout,
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'release')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = (Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path

function Fail([string]$Reason) { throw $Reason }
$gitExe = (Get-Command git -ErrorAction Stop).Source
$gnuTar = Join-Path (Split-Path (Split-Path $gitExe -Parent) -Parent) 'usr/bin/tar.exe'
if (-not (Test-Path -LiteralPath $gnuTar -PathType Leaf)) { Fail 'Git for Windows GNU tar is required to preserve Unicode source paths.' }
function RunGit([string]$Directory, [string[]]$Arguments) {
    $result = & git -C $Directory @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "git failed in $Directory ($($Arguments[0]))" }
    return ($result | Out-String).Trim()
}
function ExportGitTree([string]$Directory, [string]$Revision, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    $tarFile = Join-Path $scratch ([guid]::NewGuid().ToString() + '.tar')
    & git -C $Directory archive --format=tar --output=$tarFile $Revision
    if ($LASTEXITCODE -ne 0) { Fail "git archive failed in $Directory" }
    & $gnuTar --force-local -xf $tarFile -C $Destination
    if ($LASTEXITCODE -ne 0) { Fail "tar extraction failed for $Directory" }
    Remove-Item -LiteralPath $tarFile
}
function CheckCheckout([string]$Directory, [string]$Expected) {
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) { Fail "Missing source checkout: $Directory" }
    $actual = RunGit $Directory @('rev-parse', 'HEAD')
    if ($actual -ne $Expected) { Fail "Source revision mismatch in $Directory" }
}
function ExportSubmodules([string]$Checkout, [string]$Destination) {
    $lines = @((RunGit $Checkout @('submodule', 'status', '--recursive')) -split "`n")
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^ ([0-9a-f]{40}) (.+?)(?: \(.*\))?$') {
            Fail "Uninitialized or unexpected submodule in $Checkout"
        }
        $sha = $Matches[1]
        $relative = $Matches[2].Replace('/', [IO.Path]::DirectorySeparatorChar)
        $child = Join-Path $Checkout $relative
        CheckCheckout $child $sha
        ExportGitTree $child $sha (Join-Path $Destination $relative)
    }
}
function RelativeName([string]$Base, [string]$Path) {
    return $Path.Substring($Base.Length + 1).Replace('\', '/')
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail 'git is required.' }
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$mozc = (Resolve-Path -LiteralPath $MozcCheckout).Path
$vendor = (Resolve-Path -LiteralPath $MozcVendorDirectory).Path
$dotnet = (Resolve-Path -LiteralPath $DotNetRuntimeCheckout).Path
$commit = RunGit $repo @('rev-parse', 'HEAD')
$tagCommit = RunGit $repo @('rev-parse', "refs/tags/$Tag^{commit}")
if ($commit -ne $tagCommit) { Fail 'The release tag must point to HEAD.' }
if (RunGit $repo @('status', '--porcelain', '--untracked-files=all', '--ignore-submodules=all')) {
    Fail 'Commit or remove all public-repository changes before packaging the release.'
}
$upstream = Join-Path $repo 'upstream'
$gitlink = RunGit $repo @('ls-tree', 'HEAD', 'upstream')
if ($gitlink -notmatch '^160000 commit ([0-9a-f]{40})\s+upstream$') { Fail 'Missing upstream gitlink.' }
$upstreamSha = $Matches[1]
CheckCheckout $upstream $upstreamSha
$mozcSha = (Get-Content -LiteralPath (Join-Path $upstream 'native/mozc/MOZC_COMMIT') -Raw).Trim()
if ($mozcSha -notmatch '^[0-9a-f]{40}$') { Fail 'Invalid Mozc revision file.' }
CheckCheckout $mozc $mozcSha
$dotnetSha = RunGit $dotnet @('rev-parse', 'HEAD')
$dotnetTag = RunGit $dotnet @('rev-parse', 'refs/tags/v10.0.13^{commit}')
if ($dotnetSha -ne $dotnetTag) { Fail '.NET runtime checkout must be at v10.0.13.' }
if (-not (Test-Path -LiteralPath (Join-Path $vendor '_registries') -PathType Container)) {
    Fail 'Mozc Bazel vendor directory is missing _registries. Run bazel vendor for both release targets.'
}
if (-not @(Get-ChildItem -LiteralPath $vendor -Directory | Where-Object Name -ne '_registries').Count) {
    Fail 'Mozc Bazel vendor directory has no external source repositories.'
}

# Check the native binaries used for this APK against the local build outputs.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$nativeHashes = [ordered]@{}
$apkZip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    foreach ($name in @('libMeltypeNative.so', 'libmozc_helper.so', 'libmozc_data.so')) {
        $binary = Join-Path $repo "build/jniLibs/arm64-v8a/$name"
        if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { Fail "Missing local native build output: $name" }
        $localHash = (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash.ToLowerInvariant()
        $entry = $apkZip.GetEntry("lib/arm64-v8a/$name")
        if ($null -eq $entry) { Fail "Missing native library in APK: $name" }
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $apkHash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
        finally { $stream.Dispose(); $sha.Dispose() }
        if ($name -eq 'libmozc_helper.so') {
            # AGP strips this executable during APK packaging; check both sides of that step.
            $merged = Join-Path $repo "app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/$name"
            $stripped = Join-Path $repo "app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/$name"
            if (-not (Test-Path -LiteralPath $merged -PathType Leaf) -or
                $localHash -ne (Get-FileHash -LiteralPath $merged -Algorithm SHA256).Hash.ToLowerInvariant() -or
                -not (Test-Path -LiteralPath $stripped -PathType Leaf) -or
                $apkHash -ne (Get-FileHash -LiteralPath $stripped -Algorithm SHA256).Hash.ToLowerInvariant()) {
                Fail 'APK Mozc helper differs from Gradle stripped output or its input'
            }
        } elseif ($localHash -ne $apkHash) { Fail "APK/native output mismatch: $name" }
        $nativeHashes[$name] = $apkHash
    }
} finally { $apkZip.Dispose() }

$scratch = Join-Path ([IO.Path]::GetTempPath()) ('meltype-release-source-' + [guid]::NewGuid().ToString('N'))
$bundleName = "Meltype-Enhanced-$Tag-source"
$bundle = Join-Path $scratch $bundleName
New-Item -ItemType Directory -Path $bundle -Force | Out-Null
try {
    ExportGitTree $repo $commit $bundle
    $upstreamDest = Join-Path $bundle 'upstream'
    ExportGitTree $upstream $upstreamSha $upstreamDest
    ExportSubmodules $upstream $upstreamDest
    $mozcDest = Join-Path $bundle 'external/mozc'
    ExportGitTree $mozc $mozcSha $mozcDest
    ExportSubmodules $mozc $mozcDest
    [IO.File]::WriteAllText((Join-Path $mozcDest 'MOZC_SOURCE_REVISION'), "$mozcSha`n", (New-Object System.Text.UTF8Encoding($false)))
    $dotnetDest = Join-Path $bundle 'external/dotnet-runtime'
    ExportGitTree $dotnet $dotnetSha $dotnetDest
    ExportSubmodules $dotnet $dotnetDest
    $vendorDest = Join-Path $bundle 'external/mozc-bazel-vendor'
    & (Join-Path $PSScriptRoot 'Copy-SafeMozcVendor.ps1') -Source $vendor -Destination $vendorDest
    $required = @('LICENSE', 'THIRD-PARTY-NOTICES.md', 'licenses/KOTLIN-NOTICES.md',
        'licenses/native/MOZC-LICENSE.txt', 'licenses/native/MOZC-CREDITS.html',
        'licenses/native/DOTNET-LICENSE.txt', 'licenses/native/DOTNET-THIRD-PARTY-NOTICES.txt',
        'upstream/THIRD-PARTY-NOTICES.md', 'upstream/dictionaries/japanese.txt',
        'external/mozc/LICENSE', 'external/mozc/src/MODULE.bazel',
        'external/dotnet-runtime/src/libraries/System.Private.CoreLib',
        'external/mozc/src/data/dictionary_oss/README.txt',
        'external/mozc-bazel-vendor/_registries')
    foreach ($name in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $bundle $name))) { Fail "Missing release source: $name" }
    }
    $files = @(Get-ChildItem -LiteralPath $bundle -File -Recurse -Force)
    foreach ($file in $files) {
        $name = RelativeName $bundle $file.FullName
        $localSource = $name -notmatch '^(?:upstream|external/mozc|external/dotnet-runtime|external/mozc-bazel-vendor)/'
        if ($name -match '^(?:build|app/build|release)/|(^|/)(?:\.git|\.gradle|\.cxx|bazel-(?:bin|out|testlogs))(/|$)' -or
            $name -match '(?i)^external/mozc-bazel-vendor/.*\.(?:jks|keystore|p12|pfx)$' -or
            ($localSource -and ($name -match '(^|/)(?:local\.properties|\.env(?:\..*)?)(/|$)' -or
            $name -match '(?i)\.(?:jks|keystore|p12|pfx|pem|apk|aab)$'))) {
            Fail "Forbidden file in source bundle: $name"
        }
        if ($localSource -and $file.Length -lt 2MB -and $name -match '(?i)\.(?:md|txt|kt|java|cs|c|h|cc|cpp|py|sh|ps1|kts|json|xml|bazel|bzl|properties|yml|yaml)$') {
            $body = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
            if ($body -match 'C:[\\/]Users[\\/]|-----BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{20,}') {
                Fail "Possible private path or credential in source bundle: $name"
            }
        }
    }
    $sourceHashes = foreach ($file in ($files | Sort-Object FullName)) {
        '{0}  {1}' -f (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), (RelativeName $bundle $file.FullName)
    }
    [IO.File]::WriteAllLines((Join-Path $bundle 'SOURCE-FILES.sha256'), [string[]]$sourceHashes, (New-Object System.Text.UTF8Encoding($false)))
    $manifest = [ordered]@{
        schema = 1; tag = $Tag; repositoryCommit = $commit; upstreamCommit = $upstreamSha
        mozcCommit = $mozcSha; dotnetRuntimeCommit = $dotnetSha
        dotnetSdk = '10.0.402'; nativeAotRuntimePack = '10.0.13'
        apkFile = [IO.Path]::GetFileName($apk)
        apkSha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        nativeSha256 = $nativeHashes
        sourceHashListSha256 = (Get-FileHash -LiteralPath (Join-Path $bundle 'SOURCE-FILES.sha256') -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $bundle 'RELEASE-MANIFEST.json') -Encoding utf8
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $archive = Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path "$bundleName.tar.gz"
    if (Test-Path -LiteralPath $archive) { Fail "Archive already exists: $archive" }
    & $gnuTar --force-local -czf $archive -C $scratch $bundleName
    if ($LASTEXITCODE -ne 0) { Fail 'tar compression failed.' }
    $archiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksums = @("$archiveHash  $([IO.Path]::GetFileName($archive))", "$($manifest.apkSha256)  $($manifest.apkFile)")
    [IO.File]::WriteAllLines("$archive.sha256", [string[]]$checksums, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Source archive: $archive"
    Write-Host "SHA-256: $archiveHash"
    Write-Host "APK SHA-256: $($manifest.apkSha256)"
} finally {
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($scratch.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $scratch -Leaf) -like 'meltype-release-source-*' -and
        (Test-Path -LiteralPath $scratch)) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
