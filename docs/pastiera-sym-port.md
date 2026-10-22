# Pastiera SYM レイヤー移植の出典と検証

調査対象: [palsoftware/pastiera](https://github.com/palsoftware/pastiera) の `e7d8f27ecc7253e61690b5d34f110b25dc68bb16` (2026-09-26)。リポジトリ全体のライセンス表示は GPL version 3。下表の Kotlin ファイルには個別の SPDX / copyright ヘッダーはありません。`git log -1 -- <path>` で最終変更者を確認しました。

| 機能 | Pastiera ファイル | 最終変更 (著者) | Meltypeでの扱い |
|---|---|---|---|
| SYMキーと物理キーのイベント | `inputmethod/InputEventRouter.kt`、`inputmethod/PhysicalKeyboardInputMethodService.kt` | `43cb671`、`02af786` (Patrick Zauner) | イベント処理の流れを調査。巨大なサービス本体はコピーせず、Meltypeのサービスへ接続 |
| キーと文字の対応 | `inputmethod/AlternateCharacterManager.kt` | `43cb671` (Patrick Zauner) | Map取得の構造を調査。Meltype用の小さな保存処理を実装 |
| 画面描画 | `inputmethod/StatusBarController.kt` | `02af786` (Patrick Zauner) | AOSP由来UI等への依存を避け、Meltype用ビューを独自実装 |
| ページ状態と順番 | `core/SymLayoutController.kt`、`SymPagesConfig.kt` | `43cb671` (Patrick Zauner) | 2ページの切替動作を調査。Meltype用に絵文字→特殊文字→閉じるを実装 |
| Edit Emoji Layer、保存と初期化 | `SymCustomizationActivity.kt`、`SymCustomizationScreen.kt`、`SettingsManager.kt` | `08b6749`、`08b6749`、`02af786` (Patrick Zauner) | Compose等の依存を入れず、Meltypeの既存Activity方式で独自実装 |
| 絵文字の標準割り当て | `assets/common/sym/sym_key_mappings.json` | `51a4901` (andreapalumbo) | `app/src/main/assets/pastiera/sym_key_mappings.json` へ無変更でコピー。SHA-256: `48C49CF38A3BCFB91D0E2C5B5BD57F13F0FE402FE8EABF1D30E32C6015C1A0C3` |
| 特殊文字の標準割り当て | `assets/common/sym/sym_key_mappings_page2.json` | `a1a5cbb` (Patrick Zauner) | `app/src/main/assets/pastiera/sym_key_mappings_page2.json` へ無変更でコピー。SHA-256: `E69A2B88E3C4949065A3EBAF492B7CB61925458B791AF33D96961F372EDD5AF2` |

Pastieraの上記2つのJSONはGPLv3として取り扱い、APK内で読める `THIRD-PARTY-NOTICES.md` に出典と著作権を記載します。Meltype固有の新しい Kotlin コードは GPL-3.0-or-later です。組み合わせた配布物で Pastiera 由来部分を GPL-3.0-or-later に変更したとは表示しません。Pastiera の第三者通知にある AOSP LatinIME、Material Icons、CLDR 検索データと辞書はコピーしていません。FUTO Source First License 由来と確認されたものもコピーしていません。

## 実装の境界

- Android IME は `KEYCODE_SYM` で切り替えます。Q25実機では Linux `getevent` が `MSC_SCAN=0x1d` と `KEY_RIGHTALT` を報告しました。これはAndroid `KeyEvent.scanCode` と区別して扱い、IMEでは `KEYCODE_ALT_RIGHT` と `KEYCODE_SYM` を受け付けます。
- SYMページで物理 QWERTY キーを押すか画面キーをタップすると、同じ割り当てを確定入力します。Ctrl / Alt / Meta 付きキーは通常のショートカット経路へ渡します。
- 未確定文字があれば Meltype Core で確定してからSYM文字を入力し、直前の確定履歴を破棄します。通常の日英判定・Mozc変換経路は変更しません。
- その後のQ25実機調査で右 Shift と左 Shift を区別できることを確認し、右 Shift → Ctrl を Meltype の IME 内で実装しました。公開版の検証項目は `docs/q25-checklist.md` を参照してください。
- `build/jniLibs/arm64-v8a` に既存の NativeAOT/Mozc ライブラリがあるため、今回の Kotlin/JSON 変更だけなら Android の `:app:assembleDebug` で再パッケージできます。

## 実機確認

1. PowerShell でリポジトリのルートへ移動し、JDK 17 または 21 の `JAVA_HOME` と Android SDK の `ANDROID_HOME` を設定して `.\gradlew.bat :app:assembleDebug` を実行する。`local.properties` は公開対象に含めない。
2. 生成した `app\build\outputs\apk\debug\app-debug.apk` を端末へインストールし、Meltypeを有効にする。
3. テキスト欄で SYM を1回押すと絵文字、2回で特殊文字、3回で閉じること、ページタブや閉じるボタンも動くことを確認する。
4. Q/W/Eなどの物理キーと画面キーを押し、表示と入力文字が一致することを確認する。未確定の日本語を入力してからSYM文字を選び、二重入力や変換状態の破損がないことを確認する。
5. 設定画面で絵文字と特殊文字の割り当てを変更・保存し、IMEを再起動して反映と永続化を確認する。初期化も確認する。
6. 通常の英字入力、日本語自動判定、Mozc変換、Ctrl+Space、Shift+Space、Ctrl系ショートカット、右Shift、パスワード欄での非表示を確認する。
