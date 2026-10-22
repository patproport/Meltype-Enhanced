[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ApkPath,
    [Parameter(Mandatory)][string]$AndroidSdk,
    [ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedCertificateSha256
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Fail([string]$Reason) { throw $Reason }
$repo = (Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$sdk = (Resolve-Path -LiteralPath $AndroidSdk).Path
$buildTools = @(Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending)
if ($buildTools.Count -eq 0) { Fail 'Android SDK build-tools are missing.' }
$tools = $buildTools[0].FullName
$apksigner = @('apksigner.bat', 'apksigner') | ForEach-Object { Join-Path $tools $_ } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
$aapt = @('aapt.exe', 'aapt') | ForEach-Object { Join-Path $tools $_ } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $apksigner -or -not $aapt) { Fail 'apksigner or aapt is missing from Android SDK build-tools.' }

# Capture tool output so certificate subject details and local paths are not echoed.
$previous = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $signature = (& $apksigner verify --verbose --print-certs $apk 2>&1 | Out-String)
    $signExit = $LASTEXITCODE
    $badging = (& $aapt dump badging $apk 2>&1 | Out-String)
    $aaptExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $previous }
if ($signExit -ne 0) { Fail 'APK signature verification failed.' }
if ($aaptExit -ne 0) { Fail 'APK manifest inspection failed.' }
if ($signature -notmatch '(?im)Signer #1 certificate SHA-256 digest:\s*([0-9a-f:]{64,95})') {
    Fail 'Signing certificate SHA-256 was not reported.'
}
$certSha = $Matches[1].Replace(':', '').ToLowerInvariant()
if ($ExpectedCertificateSha256 -and $certSha -ne $ExpectedCertificateSha256.ToLowerInvariant()) {
    Fail 'Signing certificate SHA-256 differs from the expected release key.'
}
if ($badging -notmatch "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") {
    Fail 'APK package/version could not be read.'
}
$applicationId, $versionCode, $versionName = $Matches[1], $Matches[2], $Matches[3]
if ($applicationId -ne 'io.github.patproport.meltypeenhanced' -or $versionCode -ne '1' -or $versionName -ne '0.1.0') {
    Fail 'APK application ID or version differs from v0.1.0 release settings.'
}
if ($badging -match '(?m)^application-debuggable') { Fail 'APK is debuggable.' }
if ($badging -match "uses-permission: name='android.permission.INTERNET'") { Fail 'Unexpected INTERNET permission.' }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $expectedLibs = @('libMeltypeNative.so', 'libmeltype_jni.so', 'libmozc_helper.so', 'libmozc_data.so')
    $libEntries = @($zip.Entries | Where-Object { $_.FullName -match '^lib/[^/]+/[^/]+$' } |
        ForEach-Object { $_.FullName })
    $expectedEntries = @($expectedLibs | ForEach-Object { "lib/arm64-v8a/$_" })
    if (@(Compare-Object ($libEntries | Sort-Object) ($expectedEntries | Sort-Object)).Count -gt 0) {
        Fail 'APK native libraries or ABI differ from the reviewed arm64 set.'
    }
    $nativeHashes = [ordered]@{}
    foreach ($name in $expectedLibs) {
        $entry = $zip.GetEntry("lib/arm64-v8a/$name")
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
        finally { $stream.Dispose(); $sha.Dispose() }
        $nativeHashes[$name] = $hash
        if ($name -ne 'libmeltype_jni.so') {
            $built = Join-Path $repo "build/jniLibs/arm64-v8a/$name"
            if (-not (Test-Path -LiteralPath $built -PathType Leaf)) {
                Fail "Missing local native build output: $name"
            }
            if ($name -eq 'libmozc_helper.so') {
                # AGP strips the executable named lib*.so before packaging it.
                $merged = Join-Path $repo "app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/$name"
                $stripped = Join-Path $repo "app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/$name"
                if (-not (Test-Path -LiteralPath $merged -PathType Leaf) -or
                    (Get-FileHash -LiteralPath $built -Algorithm SHA256).Hash -ne
                    (Get-FileHash -LiteralPath $merged -Algorithm SHA256).Hash -or
                    -not (Test-Path -LiteralPath $stripped -PathType Leaf) -or
                    (Get-FileHash -LiteralPath $stripped -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
                    Fail "APK Mozc helper differs from Gradle's stripped output or its input"
                }
            } elseif ((Get-FileHash -LiteralPath $built -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
                Fail "APK native library differs from local build output: $name"
            }
        }
    }
    $notices = [ordered]@{
        'THIRD-PARTY-NOTICES.md' = 'THIRD-PARTY-NOTICES.md'
        'LICENSE' = 'LICENSE'
        'UPSTREAM-THIRD-PARTY-NOTICES.md' = 'upstream/THIRD-PARTY-NOTICES.md'
        'MOZC-LICENSE.txt' = 'licenses/native/MOZC-LICENSE.txt'
        'MOZC-CREDITS.html' = 'licenses/native/MOZC-CREDITS.html'
        'DOTNET-LICENSE.txt' = 'licenses/native/DOTNET-LICENSE.txt'
        'DOTNET-THIRD-PARTY-NOTICES.txt' = 'licenses/native/DOTNET-THIRD-PARTY-NOTICES.txt'
        'KOTLIN-NOTICES.md' = 'licenses/KOTLIN-NOTICES.md'
        'APACHE-2.0.txt' = 'licenses/APACHE-2.0.txt'
        'BOOST-1.0.txt' = 'licenses/BOOST-1.0.txt'
    }
    foreach ($name in $notices.Keys) {
        $entry = $zip.GetEntry("assets/notices/$name")
        if ($null -eq $entry -or $entry.Length -eq 0) { Fail "Missing or empty APK notice: $name" }
        $source = Join-Path $repo $notices[$name]
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { Fail "Missing notice source: $name" }
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose(); $sha.Dispose() }
        if ($hash -ne (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash) {
            Fail "APK notice differs from reviewed source: $name"
        }
    }
    foreach ($name in @('sym_key_mappings.json', 'sym_key_mappings_page2.json')) {
        if ($null -eq $zip.GetEntry("assets/pastiera/$name")) { Fail "Missing Pastiera mapping: $name" }
    }
} finally { $zip.Dispose() }

$apkSha = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "APK SHA-256: $apkSha"
Write-Host "Signing certificate SHA-256: $certSha"
Write-Host "Application ID: $applicationId; version: $versionName ($versionCode); ABI: arm64-v8a"
Write-Host "Verified $($notices.Count) notices and $($expectedLibs.Count) native libraries."
foreach ($name in $nativeHashes.Keys) { Write-Host "$name SHA-256 $($nativeHashes[$name])" }
