// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyBindingsTest {
    private val defaults = BindingOptions()
    private fun match(keyCode: Int, meta: Int = 0, options: BindingOptions = defaults) =
        KeyBindings.match(keyCode, meta, options)

    @Test
    fun zenkakuHankakuToggles() {
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU))
    }

    @Test
    fun ctrlZenkakuHankakuPauses() {
        assertEquals(BindingAction.TogglePause, match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun eisuGoesDirect() {
        assertEquals(BindingAction.SetDirect(true), match(KeyEvent.KEYCODE_EISU))
    }

    @Test
    fun roleKeys() {
        assertEquals(BindingAction.RoleKey(0x1C, fallbackDirect = false), match(KeyEvent.KEYCODE_HENKAN))
        assertEquals(BindingAction.RoleKey(0x1D, fallbackDirect = true), match(KeyEvent.KEYCODE_MUHENKAN))
        assertEquals(BindingAction.RoleKey(0x15, fallbackDirect = false), match(KeyEvent.KEYCODE_KATAKANA_HIRAGANA))
    }

    @Test
    fun ctrlSpaceAndShiftSpaceToggle() {
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON))
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON))
        assertEquals(
            BindingAction.ToggleDirect,
            match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON),
        )
    }

    @Test
    fun spaceTogglesCanBeTurnedOff() {
        val off = BindingOptions(shiftSpaceToggles = false, ctrlSpaceToggles = false)
        assertNull(match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON, options = off))
        assertNull(match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON, options = off))
    }

    @Test
    fun unmodifiedDollarTogglesWhenEnabled() {
        assertEquals(BindingAction.ToggleDirect, KeyBindings.match(KeyEvent.KEYCODE_UNKNOWN, 0, defaults, 0x24))
    }

    @Test
    fun dollarToggleCanBeTurnedOffAndModifiedDollarIsNotBound() {
        val off = defaults.copy(dollarKeyToggles = false)
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_UNKNOWN, 0, off, 0x24))
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_UNKNOWN, KeyEvent.META_SHIFT_ON, defaults, 0x24))
    }

    @Test
    fun q25GraveTogglesWhenEnabled() {
        assertEquals(
            BindingAction.ToggleDirect,
            KeyBindings.match(KeyEvent.KEYCODE_GRAVE, 0, defaults, 0, 41, "Q25_keyboard"),
        )
    }

    @Test
    fun q25GraveToggleCanBeTurnedOffAndModifiedGraveIsNotBound() {
        val off = defaults.copy(dollarKeyToggles = false)
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_GRAVE, 0, off, 0, 41, "Q25_keyboard"))
        for (modifier in listOf(
            KeyEvent.META_SHIFT_ON, KeyEvent.META_CTRL_ON, KeyEvent.META_ALT_ON, KeyEvent.META_META_ON,
        )) {
            assertNull(KeyBindings.match(KeyEvent.KEYCODE_GRAVE, modifier, defaults, 0, 41, "Q25_keyboard"))
        }
    }

    @Test
    fun graveFromOtherDeviceOrScanCodeIsNotBound() {
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_GRAVE, 0, defaults, 0, 41, "Other keyboard"))
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_GRAVE, 0, defaults, 0, 42, "Q25_keyboard"))
    }

    @Test
    fun plainSpaceIsNotBound() {
        assertNull(match(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun q25SymLettersAreNotBound() {
        // Q25 の Sym は右 Alt (docs/q25-keys.md)。Key Mapper などが使うこともあるので割り当てない
        val sym = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
        assertNull(match(KeyEvent.KEYCODE_K, sym))
        assertNull(match(KeyEvent.KEYCODE_E, sym))
    }

    @Test
    fun altLettersAreNotBound() {
        // Q25 の Alt+文字 は数字・記号なので、Meltype の割り当てに使わない
        assertNull(match(KeyEvent.KEYCODE_E, KeyEvent.META_ALT_ON))
        assertNull(match(KeyEvent.KEYCODE_K, KeyEvent.META_ALT_ON))
    }
}
