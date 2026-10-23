# Meltype-Enhanced

**Unofficial Android fork of Meltype Android, enhanced for Zinwa Q25.**

Meltype Enhanced is an independent, unofficial Android fork. It is not an official release of [Meltype](https://github.com/yksr-melt/Meltype) or the [original Meltype for Android port](https://github.com/345ml/meltype-android). Report issues with this fork here rather than to either upstream project.

The application is a hardware keyboard IME for Android 10+ on arm64 devices. It uses the original Meltype core for Japanese/English input and Mozc for kana to kanji conversion. It has no Android `INTERNET` permission. The signed **v0.1.0** APK has been built and tested on a Zinwa Q25; GitHub publication is pending the source-access and privacy checks.

## Zinwa Q25 enhancements

- SYM / right Alt cycles through emoji, symbols, and normal input. Both physical keys and the on-screen grid work; assignments can be edited and reset.
- A single press of the physical `$` / GRAVE key switches Japanese and direct input when enabled. Shift+Space remains available.
- Right Shift with A, C, V, X, Z, or Y sends the corresponding Ctrl shortcut through the IME. Left Shift keeps its normal behavior. The destination app must support the forwarded key events.
- Light, dark, and system theme modes cover the candidate bar, SYM grid, editor, and settings.

The signed v0.1.0 Release APK was checked on a Zinwa Q25 for Japanese and English input, Mozc conversion and candidate selection, SYM layers and mapping edits, GRAVE language switching, right-Shift Ctrl shortcuts, all three themes, and the license screen. No defects were reported. See [the checklist](docs/q25-checklist.md).

## Install

Download the signed APK from this project's [Releases](https://github.com/patproport/Meltype-Enhanced/releases) when v0.1.0 is published. Check its SHA-256 against the value below, install it, then open **Meltype Enhanced** and follow the on-screen steps to enable and select the IME. Type with the Q25's built-in keyboard or another physical keyboard; this version has no general-purpose on-screen keyboard. Space selects a conversion candidate and Enter commits it. The original Android port can remain installed because this fork has a different application ID; its settings and learned words are not migrated.

## Build from source

Clone this repository with its fixed Meltype submodule. NativeAOT and Mozc build inputs are under `core-native/`, `mozc/`, and `docker/`. `mozc/build.sh` fetches the Mozc commit fixed by `upstream/native/mozc/MOZC_COMMIT`; Bazel fetches its versioned source dependencies. The NativeAOT runtime pack is version 10.0.13. [Source access and fixed revisions](docs/RELEASE-SOURCE.md) lists the corresponding source and direct download instructions.

```sh
git clone --recurse-submodules https://github.com/patproport/Meltype-Enhanced.git
cd Meltype-Enhanced
git checkout v0.1.0
git submodule update --init --recursive
docker build --platform linux/amd64 -t meltype-build docker
docker/run.sh core-native/build.sh
docker/run.sh mozc/build.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows users can use `build-windows.ps1` after installing JDK 17 or 21, Android SDK Platform 35, NDK 27.1.12297006, CMake 3.22.1, and Docker. It accepts `-PreflightOnly` to check the environment before the full native build. Local SDK paths belong in the environment or an ignored `local.properties` file. `tools/e2e.sh` is an optional connected-device Debug test.

The signed v0.1.0 Release build and APK inspection procedure is in [docs/RELEASE-APK.md](docs/RELEASE-APK.md). It uses an external keystore, verifies notices and signing, and checks the APK before device testing.

## Licensing and provenance

This fork is distributed under **GPL-3.0-or-later**; see [LICENSE](LICENSE). It modifies the original Android port for Zinwa Q25; the dated summary is in [CHANGES.md](CHANGES.md). The original Meltype source remains credited in its submodule. The original Android port's copyright attribution is retained below. The two default SYM mapping JSON files come from [Pastiera](https://github.com/palsoftware/pastiera); their origin and checksums are recorded in [docs/pastiera-sym-port.md](docs/pastiera-sym-port.md). Other component and dictionary notices are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

- Original Meltype: Copyright (C) 2026 雪代 / Yukishiro, GPL-3.0-or-later.
- Original Meltype for Android port: Copyright (C) 2026 Keisuke Horota, GPL-3.0-or-later.
- Pastiera SYM mapping data: Pastiera contributors, GPL version 3; see the notice and source record.

The signed Release APK has SHA-256 `ca03480e438593f27b48f0c2005fcb5d97d1f7c8c8d56745bea0116fbeb4c6cd`. Its corresponding source is the tagged repository, fixed Meltype submodule, fixed Mozc and .NET Runtime sources, and versioned dependencies described in [docs/RELEASE-SOURCE.md](docs/RELEASE-SOURCE.md). The automatically generated GitHub source ZIP alone omits the submodule and build-fetched sources; use the linked instructions. The Release page must keep these source directions beside the APK for as long as the APK is distributed.
