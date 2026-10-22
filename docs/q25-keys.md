# Zinwa Q25 のキー (実測)

2026-10-15 に 調査用キーイベント計測アプリで測った値。端末: Zinwa Q25 (Android 14, model Q25)。
キーボードは `Q25_keyboard` (内蔵, source=0x101 KEYBOARD)。配列ファイルは `/system/usr/keylayout/Q25_keyboard.kl` (汎用の PC 配列とほぼ同じ) と `/system/usr/keychars/Q25_keyboard.kcm`。

| 押したキー | keyCode | metaState | 文字 |
|---|---|---|---|
| a | `KEYCODE_A` (29) | 0x0 | `a` |
| Shift を押しながら a | `KEYCODE_SHIFT_LEFT` (59) → `KEYCODE_A` | 0x41 | `A` |
| Shift を押して離してから a | `KEYCODE_SHIFT_LEFT` の DOWN/UP → `KEYCODE_A` | 0x0 | `a` (かからない) |
| Alt を押しながら e | `KEYCODE_ALT_LEFT` (57) → `KEYCODE_E` | 0x12 | `2` (kcm の alt 層) |
| Alt を押して離してから q | `KEYCODE_ALT_LEFT` の DOWN/UP → `KEYCODE_Q` | 0x0 | `q` (かからない) |
| Sym | `KEYCODE_ALT_RIGHT` (58) | 0x22 | なし |
| Sym を押しながら k | `KEYCODE_ALT_RIGHT` → `KEYCODE_VOLUME_UP` (24) | 0x22 | なし (k は届かない) |
| Sym を押しながら e | `KEYCODE_ALT_RIGHT` だけ | 0x22 | なし (e は届かない) |
| Shift を押しながら Space | `KEYCODE_SHIFT_LEFT` → `KEYCODE_SPACE` (62) | 0x41 | ` ` |
| Space | `KEYCODE_SPACE` (62) | 0x0 | ` ` |
| Enter | `KEYCODE_ENTER` (66) | 0x0 | `\n` |
| Backspace | `KEYCODE_DEL` (67) | 0x0 | なし |
| トラックパッド 上 / 下 / 左 / 右 | `KEYCODE_DPAD_UP` (19) / `DOWN` (20) / `LEFT` (21) / `RIGHT` (22) | 0x0 | なし (Q25_keyboard から KeyEvent で届く。トラックボールの MotionEvent ではない) |
| トラックパッドを押し込む | `KEYCODE_ENTER` (66) | 0x0 | `\n` (`DPAD_CENTER` ではない) |

## 分かったこと

- Shift / Alt を押して離しても次のキーにかからない (OS が覚えない)。押しながら打つ。
- Sym は右 Alt。Sym+文字 は文字のキーが届かない、または別のキーになる。測った端末では Key Mapper (ユーザー補助) が動いていて、Sym+K が音量アップになっていた。Key Mapper の割り当てか Q25 の元からの動きかは分けられていない。
- Alt を離したあと、`device=Virtual` の `KEYCODE_ALT_LEFT` DOWN/UP (metaState 0x0) が 1 組届く。Key Mapper が入れているものと思われる。文字にならないので Meltype はそのままアプリに渡す。

## Meltype の割り当てへの影響

- Sym+K / Sym+E (カタカナ / 英数) はやめて、候補バーの「カナ」「英数」ボタンにした。
- Alt+文字 は数字・記号の層なので、これまで通り割り当てない。
- トラックパッドの上下左右は矢印キー、押し込みは Enter として、今の作りのまま候補を選んで確定できる。

## LineageOS 23.2 での SYM 移植時の確認点

2026-10-15 の表は Android 14 / Key Mapper を使った時点の実測値です。以前は `KEYCODE_SYM` / `scanCode=253` と報告されましたが、2026-10-22 の実機 `getevent` では `/dev/input/event7` (`Q25_keyboard`) の `MSC_SCAN=0x1d` に続き `KEY_RIGHTALT` DOWN/UP が確認されました。SYM レイヤーは `KEYCODE_SYM` と `KEYCODE_ALT_RIGHT` の両方を受け付けます。`MSC_SCAN` はLinuxの `EV_MSC` 値で、Android `KeyEvent.scanCode` とは別フィールドです。

その後の実測では、左 Shift は `KEYCODE_SHIFT_LEFT` (59) / scanCode 42 / metaState `0x41`、右 Shift は `KEYCODE_SHIFT_RIGHT` (60) / scanCode 54 / metaState `0x81`。右 Shift+A の A は `KEYCODE_A` (29) / scanCode 30 / metaState `0x81` でした。Meltype の右 Shift → Ctrl は Q25 の右 Shift と A/C/V/X/Z/Y に限り、IME 内で処理します。左 Shift は通常の Shift のままです。SYM 中の QWERTY は Ctrl / Alt / Meta を伴うとき割り当てを適用せず、ショートカット経路へ渡します。

Q25 の物理 $ キーは `KEYCODE_GRAVE` (68) / scanCode 41 / metaState `0x0` / unicode `0x0` として届きます。設定 ON の単押しで日英切り替えが実機確認済みです。公開版の検証項目は `docs/q25-checklist.md` を参照してください。
