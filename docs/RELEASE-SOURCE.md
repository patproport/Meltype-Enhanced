# v0.1.0 source access and release

The signed APK has SHA-256 `ca03480e438593f27b48f0c2005fcb5d97d1f7c8c8d56745bea0116fbeb4c6cd`. Publish the APK and these source directions together on the v0.1.0 GitHub Release. The repository's automatic source ZIP is not complete by itself: it omits the Meltype submodule and sources fetched during the native build.

## What constitutes the corresponding source

| Class | Source or tool | v0.1.0 access |
| --- | --- | --- |
| A: release repository | Android IME, JNI, NativeAOT bridge, Mozc helper adaptation, Pastiera mappings, notices, Gradle/Docker/build scripts | The `v0.1.0` tag of this repository. Keep the tag and source available alongside the APK. |
| A: fixed submodule | Original Meltype core, dictionaries, original notices | `upstream` gitlink `45e14f86558aa6ea34f2c2ecd038ba95ca8c3c46`; clone recursively. |
| B: separate supplemental source | None identified while the fixed sources in class C provide equivalent, maintained download access | If any required fixed source cannot be obtained, attach that exact source as a separate Release asset before offering the APK. |
| C: fixed external source | Mozc and its OSS dictionary inputs | [Mozc commit `a069a88d4cb5c011de0f9aebb6c149a1c808d904`](https://github.com/google/mozc/tree/a069a88d4cb5c011de0f9aebb6c149a1c808d904), including recursive submodules. The same SHA is in `upstream/native/mozc/MOZC_COMMIT`; `mozc/build.sh` checks it. |
| C: fixed external source | .NET NativeAOT runtime code statically included in `libMeltypeNative.so` | [.NET Runtime commit `6aa72daa24f66c01c3d7b6a32519e9d21396deec`](https://github.com/dotnet/runtime/tree/6aa72daa24f66c01c3d7b6a32519e9d21396deec), tag `v10.0.13`. The reviewed build used SDK `10.0.402` and NativeAOT runtime pack `10.0.13`. This source access is required in addition to the MIT and third-party notices. |
| C: versioned external source | Mozc's linked Bazel modules and Kotlin runtime libraries packaged in the APK | Mozc `src/MODULE.bazel`, its overrides and [Bazel Central Registry](https://registry.bazel.build/) select the module source versions. `mozc/build.sh` fetches them. Kotlin versions and direct source links are in `licenses/KOTLIN-NOTICES.md`; Gradle files pin the Android build inputs. Mozc's dictionary and linked-library notices remain in `licenses/native/MOZC-CREDITS.html`. |
| D: build environment | Docker image, Android SDK/NDK, JDK, Bazel, Gradle, caches, Bazel-generated repositories and prebuilt build tools | Obtain as build tools using the committed scripts and version records. They are not copied into the APK's corresponding source. The Docker base image digest is unknown and is not inferred from the derived image ID. |

No extra source asset is required **while every fixed external source above remains freely downloadable with equivalent copying access**. GPLv3 section 6(d) permits a different server, including a third-party server, when clear directions accompany the APK. We must keep these directions and working source access available for as long as the APK is offered. If a referenced commit, submodule, or required module source becomes unavailable or access is unequal, provide its exact source through this project's Release or another maintained source location before distributing the APK. A license notice alone does not replace source access for a statically linked component. See [GPLv3 §1 and §6](https://www.gnu.org/licenses/gpl-3.0.html) and the [GNU FAQ on source at another site](https://www.gnu.org/licenses/gpl-faq.en.html).

The full .NET Runtime repository, the 49-repository Bazel vendor cache, and a Docker image are not mandatory Release assets. The exact runtime source that contributed to NativeAOT and the source of Mozc libraries linked into the helper **must remain accessible**. For v0.1.0, the fixed official .NET and Mozc source trees supply a simple complete route without attempting to prune individual source files. The Bazel vendor copy is a local offline build aid; its generated links, copied repositories, JAR build workers, and prebuilt `protoc` are not themselves APK components. The official module sources and their notices remain accessible through the fixed Mozc module definitions and registry. Do not treat a failed source URL as satisfied by a cached binary.

## Obtain and build

At release time, use the published `v0.1.0` tag. On a case-sensitive Linux filesystem:

```sh
git clone --recurse-submodules https://github.com/patproport/Meltype-Enhanced.git
cd Meltype-Enhanced
git checkout v0.1.0
git submodule update --init --recursive
git -C upstream rev-parse HEAD
git clone https://github.com/google/mozc.git build/mozc-src
git -C build/mozc-src checkout a069a88d4cb5c011de0f9aebb6c149a1c808d904
git -C build/mozc-src submodule update --init --recursive
git clone https://github.com/dotnet/runtime.git build/dotnet-runtime-source
git -C build/dotnet-runtime-source checkout 6aa72daa24f66c01c3d7b6a32519e9d21396deec
```

The last clone supplies source for inspection and modification; the unmodified NativeAOT runtime pack is downloaded by the SDK during the build. For a normal build, install JDK 17 or 21, Android SDK Platform 35, NDK `27.1.12297006`, CMake `3.22.1`, and Docker Linux/amd64. `docker/Dockerfile` installs Linux NDK r27b and Bazelisk v1.25.0; Bazel 9.0.2 was used for the reviewed Mozc dependency analysis. The .NET build script checks SDK `10.0.402`. Set the Android SDK location outside Git or in ignored `local.properties`.

```sh
docker build --platform linux/amd64 -t meltype-build docker
docker/run.sh core-native/build.sh
docker/run.sh mozc/build.sh
./gradlew :app:verifyReleaseNotices :app:testDebugUnitTest :app:assembleDebug
```

`mozc/build.sh` fetches the fixed Mozc source into a Docker volume if it is not already there. To use the inspected local checkout instead, mount it into the container and set `MOZC_SRC` to that mount path. It applies the committed helper changes and Bazel downloads the versioned modules. The reviewed offline `build --nobuild` analysis completed for `//data_manager/oss` with `--config release_build` and `//meltype.arm64` with `--config oss_android --config release_build --linkopt=-Wl,-z,max-page-size=16384`, using a local vendor directory and `--network none`. This confirms dependency resolution, not a fresh native build.

Release signing is documented in [RELEASE-APK.md](RELEASE-APK.md). It uses a key outside the repository; the existing signed APK must not be rebuilt merely to publish this source. Exact byte-for-byte reproducibility is not a publication gate, but the APK's app ID, native libraries, notices, signing certificate, and SHA-256 must match the reviewed release record.

## Release page and optional offline mirror

Place the APK SHA-256 and a link to this document beside the APK on the GitHub Release. State that the tagged repository must be cloned with submodules, and link the fixed Mozc and .NET commits above. Verify those links and the Kotlin/Mozc source access immediately before publishing. Preserve the APK, tag, and directions while v0.1.0 is distributed. Do not attach signing material, local `HANDOFF.md`, build directories, personal paths, or private Git history.

`tools/New-ReleaseSource.ps1`, `tools/Test-ReleaseSource.ps1`, and `tools/Copy-SafeMozcVendor.ps1` are retained only as an **optional full offline source mirror**. They copy and hash the entire .NET checkout and Bazel vendor tree; this can take hours. They are not required for this GitHub repository plus fixed-source-link release method, and no new full mirror should be started for v0.1.0. Their prior vendor audit found 54 internal links safe to materialize, one generated cache link to exclude, and a public debug keystore test fixture to omit. The local mirror is not a substitute for checking the Release page's live source links.

Before publication, review the public tag's tracked files for personal data and credentials, verify `HANDOFF.md` is ignored, verify the APK SHA-256, and confirm the same fixed sources remain downloadable. If any corresponding source is unavailable, add the smallest exact supplemental source asset needed to the Release before publishing the APK.
