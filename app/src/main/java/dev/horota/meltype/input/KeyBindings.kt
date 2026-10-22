// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyEvent

/** Core に渡す前に Meltype が自分で処理するキー。 */
sealed interface BindingAction {
    /** 英数 (直接入力) ⇔ 日本語。 */
    data object ToggleDirect : BindingAction

    data class SetDirect(val direct: Boolean) : BindingAction

    /** 変換・無変換・かな。入力中なら Core (meltype_role_key) が処理し、そうでなければ SetDirect(fallbackDirect)。 */
    data class RoleKey(val vk: Int, val fallbackDirect: Boolean) : BindingAction

    /** Meltype を一時停止する / 戻す (停止中はキーをすべてアプリに渡す)。 */
    data object TogglePause : BindingAction
}

data class BindingOptions(
    val shiftSpaceToggles: Boolean = true,
    val ctrlSpaceToggles: Boolean = true,
    val dollarKeyToggles: Boolean = true,
)

/**
 * 外付けキーボード (JIS / US) と Zinwa Q25 (BlackBerry 配列) の割り当て。両方を同時に有効にする (ぶつからない)。
 * Q25 の実測は docs/q25-keys.md。Alt+文字 は Q25 で数字・記号になるので使わない。
 * SYM (KEYCODE_SYM / Q25 の右 Alt) は IME サービスのレイヤーで処理する。
 */
object KeyBindings {
    fun match(
        keyCode: Int,
        metaState: Int,
        options: BindingOptions,
        unicodeChar: Int = 0,
        scanCode: Int = 0,
        deviceName: String? = null,
    ): BindingAction? {
        val ctrl = metaState and KeyEvent.META_CTRL_ON != 0
        val shift = metaState and KeyEvent.META_SHIFT_ON != 0
        val alt = metaState and KeyEvent.META_ALT_ON != 0
        val meta = metaState and KeyEvent.META_META_ON != 0
        if (unicodeChar == 0x24 && options.dollarKeyToggles && !ctrl && !shift && !alt && !meta) {
            return BindingAction.ToggleDirect
        }
        if (keyCode == KeyEvent.KEYCODE_GRAVE && scanCode == 41 && deviceName == "Q25_keyboard" &&
            options.dollarKeyToggles && !ctrl && !shift && !alt && !meta
        ) {
            return BindingAction.ToggleDirect
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> if (ctrl) BindingAction.TogglePause else BindingAction.ToggleDirect
            KeyEvent.KEYCODE_EISU -> BindingAction.SetDirect(true)
            KeyEvent.KEYCODE_HENKAN -> BindingAction.RoleKey(0x1C, fallbackDirect = false)
            KeyEvent.KEYCODE_MUHENKAN -> BindingAction.RoleKey(0x1D, fallbackDirect = true)
            KeyEvent.KEYCODE_KATAKANA_HIRAGANA -> BindingAction.RoleKey(0x15, fallbackDirect = false)
            KeyEvent.KEYCODE_SPACE -> when {
                ctrl && !alt && options.ctrlSpaceToggles -> BindingAction.ToggleDirect
                shift && !ctrl && !alt && options.shiftSpaceToggles -> BindingAction.ToggleDirect
                else -> null
            }
            else -> null
        }
    }
}
