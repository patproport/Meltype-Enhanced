[CmdletBinding()]
param([Parameter(Mandatory)][string]$SourceRepository)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$publicRoot = (Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path
$sourceRoot = (Resolve-Path -LiteralPath $SourceRepository).Path
if ($publicRoot -eq $sourceRoot) { throw 'Source and destination repositories must differ.' }

$requiredUpstream = '45e14f86558aa6ea34f2c2ecd038ba95ca8c3c46'
foreach ($root in @($publicRoot, $sourceRoot)) {
    $revision = (& git -C (Join-Path $root 'upstream') rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $revision -ne $requiredUpstream) { throw 'Upstream source revision mismatch.' }
}
$sourceFiles = @(
    'core-native/Meltype.Android.Native.csproj', 'core-native/AndroidExports.cs',
    'mozc/android_helper.py', 'mozc/BUILD.bazel.in',
    'upstream/native/mozc/MOZC_COMMIT', 'upstream/native/mozc/meltype_mozc_helper.cc',
    'upstream/native/mozc/BUILD.fragment'
)
foreach ($name in $sourceFiles) {
    $publicFile = Join-Path $publicRoot $name
    $oldFile = Join-Path $sourceRoot $name
    if (-not (Test-Path -LiteralPath $publicFile -PathType Leaf) -or
        -not (Test-Path -LiteralPath $oldFile -PathType Leaf) -or
        (Get-FileHash -LiteralPath $publicFile -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $oldFile -Algorithm SHA256).Hash) {
        throw "Native source mismatch: $name"
    }
}
foreach ($name in @('MOZC-LICENSE.txt', 'MOZC-CREDITS.html', 'DOTNET-LICENSE.txt', 'DOTNET-THIRD-PARTY-NOTICES.txt')) {
    $reviewed = Join-Path $publicRoot "licenses/native/$name"
    $generated = Join-Path $sourceRoot "build/notices/$name"
    if (-not (Test-Path -LiteralPath $generated -PathType Leaf) -or
        (Get-FileHash -LiteralPath $reviewed -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $generated -Algorithm SHA256).Hash) {
        throw "Native notice mismatch: $name"
    }
}
$deps = Join-Path $sourceRoot 'build/dotnet/bin/Meltype.Android.Native/release_linux-bionic-arm64/MeltypeNative.deps.json'
if (-not (Test-Path -LiteralPath $deps -PathType Leaf) -or
    (Get-Content -LiteralPath $deps -Raw) -notmatch 'Microsoft\.NETCore\.App\.Runtime\.NativeAOT\.linux-bionic-arm64/10\.0\.13') {
    throw 'NativeAOT runtime pack version is not the reviewed 10.0.13.'
}

# Pin the reviewed existing binaries. A changed artifact requires a new source/notice review.
$expected = [ordered]@{
    'libMeltypeNative.so' = 'E567F5B230C6674660F45980F4F47A088EEC1D9E268A541F3F7E433CE38B20F5'
    'libmozc_helper.so' = '9F8E155DF98AA965571922ADD4D808F97F7FABFE41FFCF344EDD0222CEEEC574'
    'libmozc_data.so' = '720D0CB42651FE35692578EA5F3C752940B8357FC24EFC46230C904D2F5D5D70'
}
$destination = Join-Path $publicRoot 'build/jniLibs/arm64-v8a'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
foreach ($name in $expected.Keys) {
    $oldFile = Join-Path $sourceRoot "build/jniLibs/arm64-v8a/$name"
    if (-not (Test-Path -LiteralPath $oldFile -PathType Leaf)) { throw "Missing native binary: $name" }
    $hash = (Get-FileHash -LiteralPath $oldFile -Algorithm SHA256).Hash
    if ($hash -ne $expected[$name]) { throw "Native binary changed since review: $name" }
    $newFile = Join-Path $destination $name
    if ((Test-Path -LiteralPath $newFile) -and
        (Get-FileHash -LiteralPath $newFile -Algorithm SHA256).Hash -ne $hash) {
        throw "Destination contains a different native binary: $name"
    }
    Copy-Item -LiteralPath $oldFile -Destination $newFile -Force
    Write-Host "$name SHA-256 $($hash.ToLowerInvariant())"
}
$record = [ordered]@{
    upstreamCommit = $requiredUpstream
    mozcCommit = (Get-Content -LiteralPath (Join-Path $publicRoot 'upstream/native/mozc/MOZC_COMMIT') -Raw).Trim()
    nativeSha256 = $expected
    note = 'Imported from a prior local build. Source files and native notices matched; final Release APK and Q25 checks remain required.'
}
$record | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $publicRoot 'build/native-import.json') -Encoding utf8
Write-Host 'Existing native binaries staged under ignored build/jniLibs/arm64-v8a.'
