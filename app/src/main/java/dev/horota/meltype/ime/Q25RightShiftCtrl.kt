// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent

/** Q25の右Shiftと、入力先へ送ったCtrlショートカットのDOWN/UPを対応付ける。 */
internal class Q25RightShiftCtrl {
    enum class Down { PASS, CONSUME, SEND }
    enum class Up { PASS, CONSUME, SEND }

    private var heldDeviceId: Int? = null
    private val sentKeys = mutableMapOf<Int, Int>()
    private val ignoredRepeatUps = mutableMapOf<Int, Int>()
    private val ignoreUps = mutableMapOf<Int, Int>()
    private var ignoreRightShiftUpDeviceId: Int? = null

    fun onDown(
        keyCode: Int, scanCode: Int, deviceId: Int, deviceName: String?, enabled: Boolean,
        repeatCount: Int = 0,
    ): Down {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT && scanCode == 54 && deviceName == "Q25_keyboard") {
            if (!enabled) return Down.PASS
            ignoreRightShiftUpDeviceId = null
            heldDeviceId = deviceId
            return Down.CONSUME
        }
        if (sentKeys[keyCode] == deviceId) return Down.CONSUME
        if (!enabled || heldDeviceId != deviceId || keyCode !in SHORTCUT_KEYS) return Down.PASS
        if (repeatCount > 0) {
            ignoredRepeatUps[keyCode] = deviceId
            return Down.CONSUME
        }
        ignoredRepeatUps.remove(keyCode)
        ignoreUps.remove(keyCode)
        return if (sentKeys.putIfAbsent(keyCode, deviceId) == null) Down.SEND else Down.CONSUME
    }

    fun onUp(keyCode: Int, scanCode: Int, deviceId: Int): Up {
        if (sentKeys[keyCode] == deviceId) {
            sentKeys.remove(keyCode)
            return Up.SEND
        }
        if (ignoredRepeatUps[keyCode] == deviceId) {
            ignoredRepeatUps.remove(keyCode)
            return Up.CONSUME
        }
        if (ignoreUps[keyCode] == deviceId) {
            ignoreUps.remove(keyCode)
            return Up.CONSUME
        }
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT && scanCode == 54 && heldDeviceId == deviceId) {
            heldDeviceId = null
            return Up.CONSUME
        }
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT && scanCode == 54 && ignoreRightShiftUpDeviceId == deviceId) {
            ignoreRightShiftUpDeviceId = null
            return Up.CONSUME
        }
        return Up.PASS
    }

    fun clear() {
        ignoreUps.putAll(sentKeys)
        ignoreUps.putAll(ignoredRepeatUps)
        if (heldDeviceId != null) ignoreRightShiftUpDeviceId = heldDeviceId
        heldDeviceId = null
        sentKeys.clear()
        ignoredRepeatUps.clear()
    }

    companion object {
        private val SHORTCUT_KEYS = setOf(
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y,
        )

        fun ctrlMetaState(metaState: Int): Int {
            val withoutRightShift = metaState and
                (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON).inv()
            val leftShift = if (metaState and KeyEvent.META_SHIFT_LEFT_ON != 0) KeyEvent.META_SHIFT_ON else 0
            return withoutRightShift or leftShift or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
    }
}
