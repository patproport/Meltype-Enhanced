# v0.1.0 signed Release APK procedure

Status: the signed v0.1.0 Release APK was built and verified, and the user reports that the Q25 functions listed below passed without defects. Its SHA-256 is `ca03480e438593f27b48f0c2005fcb5d97d1f7c8c8d56745bea0116fbeb4c6cd`. GitHub publication is pending the source-access and privacy checks in `docs/RELEASE-SOURCE.md`.

## Current build inputs

- `applicationId`: `io.github.patproport.meltypeenhanced`; `versionName`: `0.1.0`; `versionCode`: `1`; ABI: `arm64-v8a`.
- Namespace/JNI remain `dev.horota.meltype`. R8 minification and resource shrinking are both disabled for v0.1.0.
- Gradle packages `build/jniLibs/arm64-v8a/libMeltypeNative.so`, `libmozc_helper.so`, `libmozc_data.so`; CMake builds `libmeltype_jni.so`. `verifyReleaseNotices` is a Release prerequisite and copies ten reviewed notices to `assets/notices/`.
- `verifyReleaseSigning` rejects an unsigned Release build, missing signing variables, a missing key file, or a key inside this repository. Key values are not printed by that task.

`tools/Import-ExistingNative.ps1` compared the old read-only build with this public tree: the fixed upstream commit, NativeAOT/Mozc input source files and four generated notices matched. It copied three pinned binaries to ignored `build/jniLibs`. Their SHA-256 values are recorded in that script and `build/native-import.json` (ignored). The earlier Debug APK's Mozc helper differs from the staged file because Android Gradle Plugin stripped symbols during packaging: the staged file matches the old `merged_native_libs/debug` output, while the APK entry matches `stripped_native_libs/debug` byte for byte. The signed Release APK passed the APK inspection and Q25 checks reported above. This reuse avoids an unnecessary native rebuild; final source access follows `docs/RELEASE-SOURCE.md`.

## Store and use the signing key

Keep the key outside this repository, back it up securely, and retain the same key for future updates with this application ID. Never commit the key, passwords, or a password-bearing command/script. The `.gitignore` excludes common key formats as a second guard. The certificate subject is visible to APK recipients, so choose only an identity intended for public release.

If no key exists, the following PowerShell example creates one interactively under the user's local application-data directory. `keytool` prompts for passwords; they are not command arguments or saved in the repository. Review the public certificate subject before first release.

```powershell
$key = Join-Path $env:LOCALAPPDATA 'Meltype-Enhanced\signing\release.p12'
if (Test-Path -LiteralPath $key) { throw 'A release key already exists; do not replace it.' }
New-Item -ItemType Directory -Force (Split-Path $key -Parent) | Out-Null
& (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair `
  -keystore $key -storetype PKCS12 -alias meltype-enhanced `
  -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Meltype Enhanced'
```

`tools/Build-ReleaseApk.ps1` prompts for the store/key passwords as secure input, puts them in process-only `MELTYPE_*` environment variables for Gradle, then restores the previous environment. It runs `verifyReleaseNotices`, JUnit, `assembleRelease`, and the APK inspection script. Do not add `--info`, `--debug`, or shell tracing to a signed build. From the public repository root:

```powershell
$sdk = Read-Host 'Android SDK directory'
$jdk = Read-Host 'JDK 17 or 21 directory'
.\tools\Build-ReleaseApk.ps1 -KeystorePath $key -KeyAlias meltype-enhanced -AndroidSdk $sdk -JdkHome $jdk
```

The signed output is `app\build\outputs\apk\release\app-release.apk` and is Git-ignored. `app/build.gradle.kts` also accepts `MELTYPE_KEYSTORE`, `MELTYPE_KEYSTORE_PASSWORD`, `MELTYPE_KEY_ALIAS`, and `MELTYPE_KEY_PASSWORD` for a controlled CI environment. All four must be set. Avoid passing passwords as Gradle flags or storing them in `gradle.properties` or `local.properties`.

## Inspect the exact APK

The build wrapper invokes the inspection script automatically. It uses Android SDK `apksigner` to verify the signature and report **only** the certificate SHA-256, `aapt` for application ID/version/debuggable status, and ZIP inspection for the one reviewed ABI, four native libraries, ten notices, and two Pastiera mappings. For the Mozc helper it compares the imported file with Gradle's merged Release native file, then Gradle's stripped Release file with the APK entry. The other two imported binaries and every notice must match the APK byte for byte. The script prints the APK/native SHA-256 values. To repeat the check or pin an already reviewed certificate:

```powershell
$env:JAVA_HOME = $jdk # apksigner needs Java; restore your previous value afterward.
.\tools\Test-ReleaseApk.ps1 `
  -ApkPath app\build\outputs\apk\release\app-release.apk -AndroidSdk $sdk
# Add -ExpectedCertificateSha256 <the verified 64-character fingerprint> on later checks.
```

Store the APK SHA-256 and certificate fingerprint in private release records; publish the APK SHA-256 alongside the tagged repository and external-source directions. Inspect the signer subject locally before publication for unintended personal details. A successful script result does not establish that the APK came from the tagged source; confirm the fixed source and native-binary provenance recorded in `docs/RELEASE-SOURCE.md`.

## Q25 Release check

The signed APK with the SHA-256 above was installed on a Zinwa Q25. The user confirmed normal Japanese and English input, Mozc conversion and candidate selection, SYM layers and assignment editing, GRAVE language switching, right-Shift Ctrl shortcuts, light/dark/system themes, and the license notice screen; no defects were found. `docs/q25-checklist.md` remains the repeatable procedure for future builds and additional edge cases. Keep the device Android version, certificate fingerprint, date, and detailed results in the release record. The independent application ID allows coexistence with the old app; it does not migrate its settings or learned data.

## Remaining release gates

1. Publish the final public commit and local `v0.1.0` tag only after the source-access instructions are reviewed. Keep the fixed Meltype, Mozc, .NET Runtime, and linked-library sources accessible as described in `docs/RELEASE-SOURCE.md`.
2. Recheck the APK hash, certificate subject, notices, and tracked-file privacy before the GitHub Release. The full offline source archive is optional, not a release gate.
