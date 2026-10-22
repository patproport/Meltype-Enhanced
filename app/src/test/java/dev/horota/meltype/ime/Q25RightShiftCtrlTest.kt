// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Q25RightShiftCtrlTest {
    private val mapper = Q25RightShiftCtrl()
    private val deviceId = 7

    private fun down(keyCode: Int, scanCode: Int = 30, name: String = "Q25_keyboard") =
        mapper.onDown(keyCode, scanCode, deviceId, name, enabled = true)

    @Test
    fun onlyMeasuredQ25RightShiftStartsCtrlHold() {
        assertEquals(
            Q25RightShiftCtrl.Down.PASS,
            mapper.onDown(KeyEvent.KEYCODE_SHIFT_RIGHT, 54, deviceId, "Q25_keyboard", enabled = false),
        )
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_SHIFT_LEFT, 42))
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54, "Other keyboard"))
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_SHIFT_RIGHT, 55))
        assertEquals(Q25RightShiftCtrl.Down.CONSUME, down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54))
        assertEquals(Q25RightShiftCtrl.Down.SEND, down(KeyEvent.KEYCODE_A))
        assertEquals(
            Q25RightShiftCtrl.Down.PASS,
            mapper.onDown(KeyEvent.KEYCODE_C, 46, deviceId + 1, "Q25_keyboard", enabled = true),
        )
    }

    @Test
    fun sixShortcutsSendOneDownAndMatchingUp() {
        down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54)
        for (key in listOf(
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y,
        )) {
            assertEquals(Q25RightShiftCtrl.Down.SEND, down(key))
            assertEquals(Q25RightShiftCtrl.Down.CONSUME, down(key))
            assertEquals(Q25RightShiftCtrl.Up.SEND, mapper.onUp(key, 30, deviceId))
            assertEquals(Q25RightShiftCtrl.Up.PASS, mapper.onUp(key, 30, deviceId))
        }
        assertEquals(Q25RightShiftCtrl.Up.CONSUME, mapper.onUp(KeyEvent.KEYCODE_SHIFT_RIGHT, 54, deviceId))
    }

    @Test
    fun keyUpStillMatchesWhenRightShiftIsReleasedFirst() {
        down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54)
        down(KeyEvent.KEYCODE_C)
        mapper.onUp(KeyEvent.KEYCODE_SHIFT_RIGHT, 54, deviceId)
        assertEquals(Q25RightShiftCtrl.Down.CONSUME, down(KeyEvent.KEYCODE_C))
        assertEquals(Q25RightShiftCtrl.Up.SEND, mapper.onUp(KeyEvent.KEYCODE_C, 30, deviceId))
    }

    @Test
    fun repeatWithoutInitialDownDoesNotSendShortcut() {
        down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54)
        assertEquals(
            Q25RightShiftCtrl.Down.CONSUME,
            mapper.onDown(KeyEvent.KEYCODE_V, 47, deviceId, "Q25_keyboard", enabled = true, repeatCount = 1),
        )
        assertEquals(Q25RightShiftCtrl.Up.CONSUME, mapper.onUp(KeyEvent.KEYCODE_V, 47, deviceId))
    }

    @Test
    fun spaceAndOtherKeysKeepExistingRouting() {
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_C))
        down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54)
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_SPACE))
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_GRAVE, 41))
        assertEquals(Q25RightShiftCtrl.Down.PASS, down(KeyEvent.KEYCODE_B))
    }

    @Test
    fun ctrlMetaRemovesOnlyRightShiftAndKeepsLeftShift() {
        val right = Q25RightShiftCtrl.ctrlMetaState(KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON)
        assertEquals(0, right and KeyEvent.META_SHIFT_MASK)
        assertTrue(right and KeyEvent.META_CTRL_ON != 0)
        assertTrue(right and KeyEvent.META_CTRL_LEFT_ON != 0)

        val both = Q25RightShiftCtrl.ctrlMetaState(
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON,
        )
        assertTrue(both and KeyEvent.META_SHIFT_ON != 0)
        assertTrue(both and KeyEvent.META_SHIFT_LEFT_ON != 0)
        assertEquals(0, both and KeyEvent.META_SHIFT_RIGHT_ON)
    }

    @Test
    fun clearingHeldKeysConsumesTheirLaterUps() {
        down(KeyEvent.KEYCODE_SHIFT_RIGHT, 54)
        down(KeyEvent.KEYCODE_X)
        mapper.clear()
        assertEquals(Q25RightShiftCtrl.Up.CONSUME, mapper.onUp(KeyEvent.KEYCODE_X, 30, deviceId))
        assertEquals(Q25RightShiftCtrl.Up.CONSUME, mapper.onUp(KeyEvent.KEYCODE_SHIFT_RIGHT, 54, deviceId))
    }
}
