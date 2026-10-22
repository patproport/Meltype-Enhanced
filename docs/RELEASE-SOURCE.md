# Release source and APK gate

Status: the signed Release APK with SHA-256 `ca03480e438593f27b48f0c2005fcb5d97d1f7c8c8d56745bea0116fbeb4c6cd` has passed APK inspection and user-reported Q25 testing. The corresponding-source bundle and local release tag are still pending. For v0.1.0, attach the APK, `Meltype-Enhanced-v0.1.0-source.tar.gz`, and its `.sha256` file to the **same GitHub Release**. Keep the source available for as long as the APK is distributed. GitHub's automatic source ZIP omits submodule and build-fetched source.

## Archive contents and revisions

`tools/New-ReleaseSource.ps1` exports the clean, tagged public commit through `git archive`. It does not copy the private development history or working-tree build outputs. It adds:

| Archive path | Source |
| --- | --- |
| `upstream/` | Meltype submodule gitlink from the public commit; currently `45e14f86558aa6ea34f2c2ecd038ba95ca8c3c46`. Includes `dictionaries/`, license, and original third-party notices. |
| `external/mozc/` | Mozc commit from `upstream/native/mozc/MOZC_COMMIT`; currently `a069a88d4cb5c011de0f9aebb6c149a1c808d904`, plus recursive submodules. Contains its OSS dictionary sources. |
| `external/mozc-bazel-vendor/` | Bazel external source repositories and registry data for the Mozc dataset and Android helper targets. A prepared vendor directory is mandatory. |
| `external/dotnet-runtime/` | .NET runtime source checkout at `v10.0.13`, corresponding to the earlier NativeAOT runtime pack record. Its actual commit SHA is recorded in the manifest. |
| Project root | Android/Kotlin, JNI, NativeAOT bridge, Mozc helper adaptations, Pastiera JSON, Gradle/Docker/build scripts, GPL text, Kotlin/Mozc/.NET and upstream dictionary notices. |

The archive also contains `RELEASE-MANIFEST.json` (tag, public/upstream/Mozc/.NET commits, APK SHA-256, three packaged native hashes, toolchain versions) and `SOURCE-FILES.sha256` (each source file). The sidecar contains archive and APK SHA-256. The generator compares the Mozc helper's local build output with Gradle's merged Release copy, then Gradle's stripped Release copy with the APK entry; the other two imported binaries must match their APK entries directly. `tools/Test-ReleaseSource.ps1` re-extracts and checks all file hashes, those three APK entries, notices, key source paths and revisions. Keep the build log and inspect the Gradle-built JNI library separately to support the APK/source correspondence claim.

The earlier native build metadata records .NET SDK `10.0.402` and NativeAOT runtime pack `10.0.13`. `core-native/build.sh` checks the SDK version. The Docker `10.0-noble` image tag is mutable and its digest has **not** been fixed. Docker uses NDK r27b and Bazelisk `1.25.0`; Gradle uses Android NDK `27.1.12297006`, CMake `3.22.1`, Gradle `8.14.3`, AGP `8.7.3`, Kotlin `2.1.0`, serialization `1.7.3`. Check the actual final versions and image digest before release.

## Prepare external source

From a PowerShell session at the public checkout, use ignored `build/release-sources` for the source inputs. Confirm the .NET tag exists and matches the actual runtime pack before treating it as reviewed.

```powershell
git submodule update --init --recursive
New-Item -ItemType Directory -Force build/release-sources | Out-Null
git clone https://github.com/google/mozc.git build/release-sources/mozc
git -C build/release-sources/mozc checkout a069a88d4cb5c011de0f9aebb6c149a1c808d904
git -C build/release-sources/mozc submodule update --init --recursive
git clone https://github.com/dotnet/runtime.git build/release-sources/dotnet-runtime
git -C build/release-sources/dotnet-runtime checkout v10.0.13
git -C build/release-sources/dotnet-runtime submodule update --init --recursive
```

Mozc's fixed `src/MODULE.bazel` declares Bazel source dependencies. A Mozc Git checkout alone is insufficient. Prepare the same fixed checkout inside the existing Linux Docker `meltype-mozc` volume, run `src/build_tools/update_deps.py`, and apply the helper additions via the existing `mozc/build.sh`. Then run Bazel `vendor` for **both** build targets with their respective release flags. Mount an ignored host folder at `/release-input` and export the resulting vendor directory to `build/release-sources/mozc-vendor`. Vendoring analyzes and downloads dependencies; it does not require a full native compilation. Follow [Bazel vendor mode](https://bazel.build/versions/8.6.0/external/vendor) for your Bazel version. The Android NDK is a separately installed build tool and must not be copied into the release source archive without a redistribution review.

```sh
cd /mozc/src
python3 build_tools/update_deps.py
bazel vendor --vendor_dir=/release-input/mozc-vendor //data_manager/oss:mozc_dataset_for_oss --config release_build
bazel vendor --vendor_dir=/release-input/mozc-vendor //meltype:meltype_mozc_helper.arm64 --config oss_android --config release_build --linkopt=-Wl,-z,max-page-size=16384
```

The second target requires the `mozc/android_helper.py`, `mozc/BUILD.bazel.in`, and upstream `native/mozc/BUILD.fragment` additions first. The release archive includes their original scripts and pristine Mozc source, with `MOZC_SOURCE_REVISION` for a source-only checkout. `mozc/build.sh` recognizes that archive input and avoids fetching Mozc again. Check vendored dependencies against both targets in a clean environment; if Bazel fetches more source, regenerate the vendor tree. The generator rejects a missing vendor directory, missing registry metadata, and unreviewed symbolic links.

## Build from the extracted archive

Extract to a **case-sensitive Linux filesystem**. Install JDK 17 or 21, Android SDK Platform 35/NDK `27.1.12297006`/CMake `3.22.1`, Docker Linux/amd64, and a reviewed .NET SDK image with SDK `10.0.402`. Provide Android SDK via `ANDROID_HOME` or ignored `local.properties`. From the extracted root:

```sh
docker build --platform linux/amd64 -t meltype-build docker
docker run --rm --platform linux/amd64 -v "$PWD":/work -w /work -v meltype-nuget:/root/.nuget meltype-build bash core-native/build.sh
docker run --rm --platform linux/amd64 -v "$PWD":/work -w /work -e MOZC_SRC=/work/external/mozc -e MOZC_BAZEL_FLAGS=--vendor_dir=/work/external/mozc-bazel-vendor -v meltype-bazel:/root/.cache/bazel meltype-build bash mozc/build.sh
./gradlew :app:verifyReleaseNotices :app:testDebugUnitTest :app:assembleRelease
```

The NativeAOT publish and Mozc dataset/helper build are required to create `build/jniLibs`; those binaries are deliberately absent from source. `mozc/build.sh` applies its helper adaptation to the extracted pristine Mozc tree once. A second build needs a fresh extraction. `build-windows.ps1` uses a Docker volume and assembles Debug by default; it is useful for preflight but is not a release verification. Signing values come only from environment variables and are never archived.

## Generate and verify the release pair

After creating the first public commit, completing the final source review, and creating a local `v0.1.0` tag on that commit, run in a clean public checkout. The tag is a deliberate prerequisite of `New-ReleaseSource.ps1`; do not create it until the release commit is final. The existing signed APK must still be checked against the tagged source:

```powershell
.\tools\New-ReleaseSource.ps1 -Tag v0.1.0 `
  -ApkPath app\build\outputs\apk\release\app-release.apk `
  -MozcCheckout build\release-sources\mozc `
  -MozcVendorDirectory build\release-sources\mozc-vendor `
  -DotNetRuntimeCheckout build\release-sources\dotnet-runtime
.\tools\Test-ReleaseSource.ps1 `
  -ArchivePath release\Meltype-Enhanced-v0.1.0-source.tar.gz `
  -ApkPath app\build\outputs\apk\release\app-release.apk
```

`release/` is Git-ignored. The generator fails if the tag is missing, HEAD does not match it, the working tree is dirty, any fixed checkout is wrong, the native files differ from the expected Gradle inputs or APK entries, or required sources/notices are absent. It rejects common generated files, keys, credentials, and Windows user paths. Manually inspect remaining text and binary files; the scan is not a complete secret detector. Record the printed APK and archive hashes in Release notes, attach the matching APK and both source files, and retain the build log and dependency report. Confirm the Release tag resolves to `repositoryCommit` in the manifest and `git ls-tree <commit> upstream` matches `upstreamCommit`.

The checksum verifier proves archive integrity and internal revision records. It cannot by itself prove that the signed APK was built from that source. Inspect the APK's actual native libraries, dictionary, Kotlin dependencies, notices, app ID/version, signing certificate, and input logging. Compare native-library hashes with the build outputs and complete the Q25 device checklist.

## Remaining P0-2 gates

1. Create the first public commit, then create the local tag after the final review and run both scripts on the real signed APK. The APK/source correspondence is not complete until these checks pass.
2. Confirm the .NET runtime source tag against the final NativeAOT pack, record the Docker image digest, and verify that the Bazel vendor set covers both Mozc targets. Rebuild from an extracted source archive and compare outputs; review any mismatches.
3. Audit the final APK's statically linked dependencies, Mozc/embedded dictionaries, Kotlin and .NET notices. Confirm any installation information relevant to APK distribution. The [GPLv3 Corresponding Source and object-code terms](https://www.gnu.org/licenses/gpl-3.0.html) require the source, build control scripts, and applicable installation information; this document does not substitute for the final artifact audit.
