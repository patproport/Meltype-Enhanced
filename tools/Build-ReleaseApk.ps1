[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$KeystorePath,
    [Parameter(Mandatory)][string]$KeyAlias,
    [Parameter(Mandatory)][string]$AndroidSdk,
    [string]$JdkHome
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = (Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path
$keystore = (Resolve-Path -LiteralPath $KeystorePath).Path
$sdk = (Resolve-Path -LiteralPath $AndroidSdk).Path
if (-not (Test-Path -LiteralPath $keystore -PathType Leaf)) { throw 'Keystore file does not exist.' }
if ([IO.Path]::GetFullPath($keystore).StartsWith([IO.Path]::GetFullPath($repo + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keystore must be stored outside the public repository.'
}
if ([string]::IsNullOrWhiteSpace($KeyAlias)) { throw 'Key alias is required.' }

$old = @{}
$oldJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
foreach ($name in @('MELTYPE_KEYSTORE', 'MELTYPE_KEYSTORE_PASSWORD', 'MELTYPE_KEY_ALIAS', 'MELTYPE_KEY_PASSWORD')) {
    $old[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    if ($JdkHome) {
        $jdk = (Resolve-Path -LiteralPath $JdkHome).Path
        if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin/java.exe') -PathType Leaf)) {
            throw 'JDK path does not contain java.exe.'
        }
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'Process')
    } elseif (-not $env:JAVA_HOME) {
        throw 'Set JAVA_HOME or pass -JdkHome for Gradle and apksigner.'
    }
    $storeSecure = Read-Host 'Keystore password' -AsSecureString
    $keySecure = Read-Host 'Key password' -AsSecureString
    $storePointer = [IntPtr]::Zero
    $keyPointer = [IntPtr]::Zero
    try {
        $storePointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storeSecure)
        $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecure)
        [Environment]::SetEnvironmentVariable('MELTYPE_KEYSTORE', $keystore, 'Process')
        [Environment]::SetEnvironmentVariable('MELTYPE_KEYSTORE_PASSWORD', [Runtime.InteropServices.Marshal]::PtrToStringBSTR($storePointer), 'Process')
        [Environment]::SetEnvironmentVariable('MELTYPE_KEY_ALIAS', $KeyAlias, 'Process')
        [Environment]::SetEnvironmentVariable('MELTYPE_KEY_PASSWORD', [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer), 'Process')
    } finally {
        if ($storePointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($storePointer) }
        if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
    }
    Push-Location -LiteralPath $repo
    try {
        & ./gradlew.bat :app:verifyReleaseNotices :app:testDebugUnitTest :app:assembleRelease --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Gradle Release build failed.' }
        $apk = Join-Path $repo 'app/build/outputs/apk/release/app-release.apk'
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Signed Release APK was not produced.' }
        & ./tools/Test-ReleaseApk.ps1 -ApkPath $apk -AndroidSdk $sdk
    } finally { Pop-Location }
} finally {
    foreach ($name in $old.Keys) { [Environment]::SetEnvironmentVariable($name, $old[$name], 'Process') }
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $oldJavaHome, 'Process')
}
