# Third-party notices

Meltype for Android は GPL-3.0-or-later です。次のものを含みます。

## Meltype (本家)
- https://github.com/yksr-melt/Meltype — Copyright (C) 2026 雪代 / Yukishiro — GPL-3.0-or-later
- 本家が同梱しているもの (辞書のデータなど) のライセンスは `upstream/THIRD-PARTY-NOTICES.md` を見てください。APK にも同じ内容を含みます。

## Mozc
- https://github.com/google/mozc — Copyright Google LLC — BSD-3-Clause
- v0.1.0 source: commit `a069a88d4cb5c011de0f9aebb6c149a1c808d904` and its submodules. The exact source-access and build instructions are in `docs/RELEASE-SOURCE.md`.
- ライセンス全文: `MOZC-LICENSE.txt` (`licenses/native/` に固定コピー。ビルド時は Mozc のソースから再生成可能)
- Mozc が使っているもののクレジット: `MOZC-CREDITS.html`

## .NET ランタイム (NativeAOT)
- https://github.com/dotnet/runtime — Copyright .NET Foundation and Contributors — MIT
- v0.1.0 NativeAOT runtime pack `10.0.13`, source tag `v10.0.13` at commit `6aa72daa24f66c01c3d7b6a32519e9d21396deec`. Some runtime code is statically included in `libMeltypeNative.so`; `docs/RELEASE-SOURCE.md` gives access to its corresponding source.
- ライセンス全文: `DOTNET-LICENSE.txt`、.NET が使っているもの: `DOTNET-THIRD-PARTY-NOTICES.txt` (`licenses/native/` に固定コピー。ビルド時は .NET SDK から再生成可能)

APK には、これらのファイルを入れています (設定画面の「ライセンス」で読めます)。GitHub Releases にも同じファイルを置きます。

## Kotlin runtime and kotlinx.serialization

- Kotlin standard library: https://github.com/JetBrains/kotlin — Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors — Apache-2.0.
- kotlinx.serialization JSON and core: https://github.com/Kotlin/kotlinx.serialization — JetBrains s.r.o. source headers and Apache-2.0.
- JetBrains annotations (if resolved into the release runtime): https://www.jetbrains.com/opensource/ — JetBrains s.r.o. source headers and Apache-2.0.
- The Kotlin standard library includes code derived from GWT and Guava under Apache-2.0, and from Boost under the Boost Software License 1.0. The specific attributions, module versions, and source links are in `KOTLIN-NOTICES.md`.
- Full license texts: `APACHE-2.0.txt` and `BOOST-1.0.txt`. These files and `KOTLIN-NOTICES.md` are also bundled in the APK license screen.

## Pastiera SYM 標準キー割り当て
- https://github.com/palsoftware/pastiera — Copyright (C) Pastiera contributors (Andrea Palumbo, Patrick Zauner ほか) — GPL version 3
- 取得時のリビジョン: `e7d8f27ecc7253e61690b5d34f110b25dc68bb16`
- `app/src/main/assets/pastiera/sym_key_mappings.json` は Pastiera の `app/src/main/assets/common/sym/sym_key_mappings.json` から変更せずに収録。絵文字レイヤーの標準割り当てです。
- `app/src/main/assets/pastiera/sym_key_mappings_page2.json` は Pastiera の `app/src/main/assets/common/sym/sym_key_mappings_page2.json` から変更せずに収録。特殊文字レイヤーの標準割り当てです。
- GPL version 3 の全文はこのリポジトリと APK の `LICENSE` に含まれます。これらの Pastiera 由来ファイルは GPLv3 として扱います。
- Pastiera の AOSP 由来UI、Googleアイコン、CLDR検索データ、辞書、FUTO Source First License 由来と確認されたコード・アセットは移植していません。
