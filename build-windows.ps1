[CmdletBinding()]
param(
    [string]$JdkHome,
    [string]$AndroidSdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [switch]$SkipUnitTests,
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = (Resolve-Path -LiteralPath $PSScriptRoot).Path

function Fail([string]$Message) { throw $Message }
function Get-JavaVersion([string]$JavaExe) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $JavaExe
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        try { $started = $process.Start() }
        catch { Fail "Unable to start Java executable '$JavaExe': $($_.Exception.Message)" }
        if (-not $started) { Fail "Unable to start Java executable '$JavaExe'." }
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $exitCode = $process.ExitCode
    } finally {
        $process.Dispose()
    }

    $text = $stdout + "`n" + $stderr
    if ($exitCode -ne 0) { Fail "java -version exited with code $exitCode. Output: $text" }
    if ($text -match 'version "([0-9]+)') { return [int]$Matches[1] }
    if ($text -match 'openjdk ([0-9]+)') { return [int]$Matches[1] }
    Fail "Unable to parse Java version from: $text"
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][object[]]$Arguments,
        [Parameter(Mandatory = $true)][ref]$ExitCode
    )
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 surfaces native stderr as error records. Keep it non-terminating;
        # the native process exit code below is the authority for success or failure.
        $ErrorActionPreference = 'Continue'
        & $FilePath @Arguments
        $ExitCode.Value = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}
if ($JdkHome) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) { Fail "JDK not found at -JdkHome: $JdkHome" }
    $javaVersion = Get-JavaVersion (Join-Path $JdkHome 'bin\java.exe')
    if ($javaVersion -notin @(17, 21)) { Fail "Use JDK 17 or 21. Found Java $javaVersion at $JdkHome." }
} else {
    $jdkCandidates = @()
    if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
    $jdkCandidates += (Join-Path $env:USERPROFILE '.jdks\jbr-21.0.11')
    $jdkCandidates += 'C:\Program Files\Android\Android Studio\jbr'
    $JdkHome = $null
    foreach ($candidate in ($jdkCandidates | Select-Object -Unique)) {
        $javaExe = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaExe)) { continue }
        $candidateVersion = Get-JavaVersion $javaExe
        if ($candidateVersion -in @(17, 21)) { $JdkHome = $candidate; $javaVersion = $candidateVersion; break }
        Write-Host "Skipping incompatible Java $candidateVersion at $candidate"
    }
    if (-not $JdkHome) { Fail 'JDK 17 or 21 not found. Install/use one and pass -JdkHome, or set JAVA_HOME.' }
}
$JdkHome = (Resolve-Path -LiteralPath $JdkHome).Path

$originalJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
$originalAndroidHome = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'Process')
$originalAndroidSdkRoot = [Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT', 'Process')
try {
    # Build-specific environment values are temporary and restored in finally.
    $env:JAVA_HOME = $JdkHome
    Write-Host "Using Java $javaVersion at $JdkHome"

    if (-not (Test-Path -LiteralPath $AndroidSdk)) { Fail "Android SDK not found: $AndroidSdk. Set ANDROID_HOME or pass -AndroidSdk." }
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

    $requiredSdkItems = @(
        (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'),
        (Join-Path $env:ANDROID_HOME 'ndk\27.1.12297006\source.properties')
    )
    foreach ($item in $requiredSdkItems) {
        if (-not (Test-Path -LiteralPath $item)) { Fail "Required Android SDK component is missing: $item. Install it using Android Studio > SDK Manager." }
    }

    $requiredCmakeVersion = '3.22.1'
    $cmakePath = Join-Path $env:ANDROID_HOME "cmake\$requiredCmakeVersion"
    if (-not (Test-Path -LiteralPath $cmakePath)) { Fail "CMake $requiredCmakeVersion is required but missing. Install it using Android Studio > SDK Manager." }
    $gradleConfig = Get-Content -LiteralPath (Join-Path $repo 'app\build.gradle.kts') -Raw
    if ($gradleConfig -notmatch '(?s)externalNativeBuild\s*\{\s*cmake\s*\{[^}]*version\s*=\s*"3\.22\.1"') {
        Fail 'Gradle must select CMake 3.22.1 because CMakeLists.txt requires version 3.22 or newer.'
    }
    Write-Host "Android SDK packages: Platform 35, NDK 27.1.12297006, CMake $requiredCmakeVersion"
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail 'Git is missing from PATH.' }
    if (-not (Test-Path -LiteralPath (Join-Path $repo 'upstream\src\Meltype.Core\Meltype.Core.csproj'))) { Fail 'Upstream Meltype submodule source is missing; run git submodule update --init --recursive.' }

    $dockerCommand = Get-Command docker -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $dockerExe = if ($dockerCommand) { $dockerCommand.Source } else { $null }
    if (-not $dockerExe) {
        $dockerCandidates = @(
            (Join-Path $env:USERPROFILE 'AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'),
            (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
        )
        $dockerExe = $dockerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (-not $dockerExe) { Fail 'Docker CLI is missing. Install and start Docker Desktop with the Linux/WSL2 backend, then rerun.' }
    $dockerInfoExitCode = 0
    Invoke-NativeCommand -FilePath $dockerExe -Arguments @('info', '--format', '{{.ServerVersion}}') -ExitCode ([ref]$dockerInfoExitCode) *> $null
    if ($dockerInfoExitCode -ne 0) { Fail 'Docker is installed but its Linux engine is not available. Start Docker Desktop and check the WSL2 backend.' }

    if ($PreflightOnly) {
        Write-Host 'Preflight checks passed. No submodule sync or build commands were run.'
        return
    }

    Write-Host 'Synchronizing upstream submodule...'
    $gitExitCode = 0
    Invoke-NativeCommand -FilePath 'git' -Arguments @('-C', $repo, 'submodule', 'update', '--init', '--recursive') -ExitCode ([ref]$gitExitCode)
    if ($gitExitCode -ne 0) { Fail 'git submodule update failed.' }

    Write-Host 'Building Linux/amd64 native build image...'
    $dockerBuildExitCode = 0
    Invoke-NativeCommand -FilePath $dockerExe -Arguments @('build', '--platform', 'linux/amd64', '-t', 'meltype-build', (Join-Path $repo 'docker')) -ExitCode ([ref]$dockerBuildExitCode)
    if ($dockerBuildExitCode -ne 0) { Fail 'Docker image build failed.' }

    $dockerRepo = $repo.Replace('\', '/')
    $commonDockerArgs = @(
        'run', '--rm', '--platform', 'linux/amd64',
        '--mount', "type=bind,source=$dockerRepo,target=/work",
        '--mount', 'type=volume,source=meltype-nuget,target=/root/.nuget',
        '--mount', 'type=volume,source=meltype-bazel,target=/root/.cache/bazel',
        '--mount', 'type=volume,source=meltype-mozc,target=/mozc',
        '-e', 'MOZC_SRC=/mozc', '-w', '/work', 'meltype-build', 'bash'
    )
    foreach ($nativeScript in @('core-native/build.sh', 'mozc/build.sh')) {
        Write-Host "Building native component: $nativeScript"
        $nativeExitCode = 0
        $nativeArguments = $commonDockerArgs + @($nativeScript)
        Invoke-NativeCommand -FilePath $dockerExe -Arguments $nativeArguments -ExitCode ([ref]$nativeExitCode)
        if ($nativeExitCode -ne 0) { Fail "Native build failed: $nativeScript" }
    }

    if (-not $SkipUnitTests) {
        Write-Host 'Running JVM unit tests...'
        $unitTestExitCode = 0
        Invoke-NativeCommand -FilePath (Join-Path $repo 'gradlew.bat') -Arguments @(':app:testDebugUnitTest') -ExitCode ([ref]$unitTestExitCode)
        if ($unitTestExitCode -ne 0) { Fail 'Gradle unit tests failed.' }
    }

    Write-Host 'Assembling Debug APK...'
    $gradleExitCode = 0
    Invoke-NativeCommand -FilePath (Join-Path $repo 'gradlew.bat') -Arguments @(':app:assembleDebug') -ExitCode ([ref]$gradleExitCode)
    if ($gradleExitCode -ne 0) { Fail 'Gradle assembleDebug failed.' }

    $apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) { Fail "Gradle completed but APK was not found: $apk" }
    $item = Get-Item -LiteralPath $apk
    Write-Host ("APK: {0} ({1:N2} MiB)" -f $item.FullName, ($item.Length / 1MB))
} finally {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $originalJavaHome, 'Process')
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $originalAndroidHome, 'Process')
    [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $originalAndroidSdkRoot, 'Process')
}
