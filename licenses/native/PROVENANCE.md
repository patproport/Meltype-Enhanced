# Native notice provenance for v0.1.0 preparation

These four files are reviewed snapshots used by `app/build.gradle.kts` to populate `assets/notices/`. They were copied byte for byte from the earlier Android build's `build/notices/`. Each SHA-256 also matches the corresponding entry in that build's Debug APK. No native component was rebuilt for this copy.

| File | SHA-256 | Generated from |
| --- | --- | --- |
| `MOZC-LICENSE.txt` | `44CDD923B91EA9199293ABECC2762C70C87DBF1E581C027A94C416368D1A648C` | Mozc `LICENSE` |
| `MOZC-CREDITS.html` | `DD7C566382204F448040095E5D3C2F2B5199A1B85D44F427CEB94277DC04AF50` | Mozc `src/data/installer/credits_en.html` |
| `DOTNET-LICENSE.txt` | `CFC21F5E8BD655AE997EEC916138B707B1D290B83272C02A95C9F821B8C87310` | .NET SDK `/usr/share/dotnet/LICENSE.txt` |
| `DOTNET-THIRD-PARTY-NOTICES.txt` | `2DC8F8C5A39401E928B5784AB564EB8B3CEB99EAD3DF8F260E0CAB7E0BBECC7A` | .NET SDK `/usr/share/dotnet/ThirdPartyNotices.txt` |

`mozc/build.sh` obtains its commit from `upstream/native/mozc/MOZC_COMMIT`: `a069a88d4cb5c011de0f9aebb6c149a1c808d904`. It copies the two Mozc notices from that checkout. The source checkout lived in a Docker volume and was not available for an independent byte comparison during this migration. The script now rejects a checkout at a different commit before building or regenerating notices. Mozc's own code is BSD-3-Clause; `MOZC-CREDITS.html` retains the separate third-party notices.

The earlier NativeAOT build's `MeltypeNative.deps.json` records `Microsoft.NETCore.App.Runtime.NativeAOT.linux-bionic-arm64/10.0.13`. Its NuGet restore metadata records .NET SDK `10.0.402`. `core-native/build.sh` copies the .NET notices from that SDK image. The Dockerfile currently names the moving `10.0-noble` SDK tag, so its image digest is not established by these files. Future regeneration must use the reviewed SDK version and compare all four outputs with these snapshots; a difference requires a new notice review.

The expected Kotlin runtime versions and their separate notices are recorded in `../KOTLIN-NOTICES.md`. This provenance record does not replace inspection of the final signed Release APK or verification of Mozc dictionary and native binary contents.
