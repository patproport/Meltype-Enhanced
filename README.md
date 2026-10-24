# Meltype Enhanced

**An unofficial Japanese IME for Android, enhanced for the Zinwa Q25.**

**Zinwa Q25向けにカスタマイズした、Android用の非公式日本語IMEです。**

[English](#english) | [日本語](#日本語)

**[Download APK / APKをダウンロード (v0.1.0)](https://github.com/patproport/Meltype-Enhanced/releases/latest)**

---

## English

### About Meltype Enhanced

Meltype Enhanced is a modified version of [Meltype for Android](https://github.com/345ml/meltype-android), based on the original [Meltype](https://github.com/yksr-melt/Meltype).

I was looking for a Japanese IME that worked well with the Zinwa Q25's physical keyboard. When I discovered Meltype, I really liked it and decided to make a few changes to better suit my own needs.

I originally made these changes for personal use, but I thought other Q25 users might find them useful too, so I decided to share the modified version along with its source code.

This is an independent, unofficial fork and is not affiliated with either of the original projects.

### Features

**Physical SYM key support**

- Press SYM to switch between emoji, special characters, and normal keyboard input.
- Customize the characters shown in the SYM layouts.
- Reset the layouts to their default settings.

**Japanese / English switching**

- Press the physical `$` key to switch between Japanese and English input.
- This behavior can be enabled or disabled in Settings.
- Shift + Space is also supported.

**Right Shift shortcuts**

Use the right Shift key with A, C, V, X, Z, or Y for familiar Ctrl shortcuts, including select all, copy, paste, cut, undo, and redo.

The left Shift key continues to work normally. Shortcut behavior depends on the app receiving the input.

**Appearance**

- Light theme
- Dark theme
- Follow system theme

**Japanese input**

- Japanese and English text input
- Kana-to-kanji conversion powered by Mozc
- Conversion candidate selection using the physical keyboard

### Compatibility

- Android 10 or later
- ARM64 devices
- Developed and tested primarily on the Zinwa Q25
- A physical keyboard is required; this version does not provide a general-purpose on-screen keyboard
- Root access is not required

Compatibility with other Android devices and physical keyboards has not been extensively tested.

### Installation

1. Download the latest APK from [GitHub Releases](https://github.com/patproport/Meltype-Enhanced/releases).
2. Install the APK on your device.
3. Open Meltype Enhanced.
4. Follow the on-screen instructions to enable and select the input method.
5. Start typing using your physical keyboard.

Meltype Enhanced can be installed alongside the original Meltype for Android. Settings and learned words are not transferred between them.

### Feedback

This project started as a personal customization for my Q25, so there may still be bugs or compatibility issues.

If you encounter a problem or have a suggestion, feel free to open an [Issue](https://github.com/patproport/Meltype-Enhanced/issues).

---

## 日本語

### Meltype Enhancedについて

Meltype Enhancedは、[Meltype for Android](https://github.com/345ml/meltype-android)をベースに、Zinwa Q25向けの機能を追加した非公式のAndroid用日本語IMEです。

もともとQ25の物理キーボードで快適に日本語を入力できるIMEを探していたところ、Meltypeに出会いました。

実際に使ってみてとても気に入ったため、自分のQ25でより使いやすくなるよう、いくつかの機能を追加・変更しました。

当初は個人用に作っていましたが、同じQ25ユーザーにも役立つかもしれないと思い、ソースコードとAPKを公開することにしました。

なお、本アプリは非公式の改変版であり、Meltype本家およびAndroid移植版の公式プロジェクトではありません。

### 主な機能

**1. 物理SYMキーへの対応**

Q25のSYMキーを使って、絵文字や特殊文字を簡単に入力できるようにしました。

- SYMキーを押すたびに「絵文字 → 特殊文字 → 通常入力」の順番で切り替え
- 表示される絵文字や記号を自由にカスタマイズ可能
- 初期設定へのリセットにも対応

**2. `$`キーで日本語・英語を切り替え**

物理キーボードの`$`キーを押すだけで、日本語入力と英語入力を切り替えられます。

- 設定画面からON/OFFを変更可能
- 従来のShift + Spaceによる切り替えも使用可能

**3. 右Shiftキーを使ったショートカット**

Q25の右Shiftキーとアルファベットキーを組み合わせて、一般的なCtrlショートカットを利用できます。

| 操作 | 機能 |
|---|---|
| 右Shift + A | すべて選択 |
| 右Shift + C | コピー |
| 右Shift + V | 貼り付け |
| 右Shift + X | 切り取り |
| 右Shift + Z | 元に戻す |
| 右Shift + Y | やり直し |

左Shiftキーは通常どおり使用できます。ショートカットの動作は入力先のアプリにも依存します。

**4. テーマ切り替え**

ライト・ダーク・システム設定に連動する3種類の表示モードに対応しています。

**5. 日本語変換**

Meltypeの日本語入力機能を引き継ぎ、Mozcによるかな漢字変換を利用できます。変換候補の選択・確定も物理キーボードから操作できます。

### 対応環境

- Android 10以降
- ARM64対応端末
- Zinwa Q25で開発・動作確認済み
- 物理キーボードが必要です
- Root権限は不要です

Q25以外のAndroid端末や物理キーボードについては、十分な動作検証を行っていません。

### インストール方法

1. [GitHub Releases](https://github.com/patproport/Meltype-Enhanced/releases)から最新のAPKをダウンロードします。
2. Android端末にAPKをインストールします。
3. Meltype Enhancedを起動します。
4. 画面の案内に従ってIMEを有効化し、使用する入力方法として選択します。
5. 物理キーボードから日本語入力を開始できます。

オリジナルのMeltype for Androidとは別のアプリとしてインストールできます。ただし、設定や学習データは引き継がれません。

### 不具合報告・フィードバック

もともと自分のQ25向けにカスタマイズしたアプリなので、まだ不具合や他の端末との互換性問題が残っている可能性があります。

不具合や改善案がありましたら、[GitHub Issues](https://github.com/patproport/Meltype-Enhanced/issues)からお知らせください。

---

## Source code / ソースコード

Meltype Enhanced is released under **GPL-3.0-or-later**.

Meltype Enhancedのソースコードは、GPL-3.0-or-laterライセンスで公開しています。

For source code, build instructions, and fixed third-party dependencies, see:

ソースコード、ビルド方法、使用している外部ソースの固定バージョンについては、以下をご参照ください。

- [Source code](https://github.com/patproport/Meltype-Enhanced)
- [Build and corresponding source instructions](docs/RELEASE-SOURCE.md)
- [License](LICENSE)
- [Third-party notices](THIRD-PARTY-NOTICES.md)
- [Changes](CHANGES.md)

To clone the repository with the original Meltype submodule:

```bash
git clone --recurse-submodules https://github.com/patproport/Meltype-Enhanced.git
```

The GitHub-generated source ZIP does not include all submodule or build-fetched sources. Please refer to the corresponding source instructions above.

## Credits / 謝辞

This project would not have been possible without the work of the original developers.

本アプリは、以下の開発者の皆様が公開してくださったプロジェクトをベースにしています。素晴らしいソフトウェアを公開してくださったことに感謝いたします。

- [Meltype](https://github.com/yksr-melt/Meltype) — Original project by Yukishiro / 雪代
- [Meltype for Android](https://github.com/345ml/meltype-android) — Android port by Keisuke Horota
- [Mozc](https://github.com/google/mozc) — Japanese input conversion engine
- [Pastiera](https://github.com/palsoftware/pastiera) — Source of the default SYM mapping data

Please report issues related to Meltype Enhanced to this repository rather than the original projects.

Meltype Enhancedに関する不具合は、元のプロジェクトではなく、このリポジトリへご報告ください。

---

## Release v0.1.0

**[Download Meltype-Enhanced-v0.1.0.apk](https://github.com/patproport/Meltype-Enhanced/releases/download/v0.1.0/Meltype-Enhanced-v0.1.0.apk)**

APK SHA-256:

`ca03480e438593f27b48f0c2005fcb5d97d1f7c8c8d56745bea0116fbeb4c6cd`

[Release details and corresponding source / リリース詳細・対応ソース](https://github.com/patproport/Meltype-Enhanced/releases/tag/v0.1.0)