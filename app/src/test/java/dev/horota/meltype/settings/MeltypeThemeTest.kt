// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MeltypeThemeTest {
    @Test fun missingOrUnknownPreferenceDefaultsToLight() {
        assertEquals(MeltypeThemeMode.LIGHT, MeltypeThemeMode.fromStorageValue(null))
        assertEquals(MeltypeThemeMode.LIGHT, MeltypeThemeMode.fromStorageValue("unknown"))
        assertEquals(MeltypeThemeMode.LIGHT, MeltypeThemeMode.fromStorageValue("light"))
        assertEquals(MeltypeThemeMode.DARK, MeltypeThemeMode.fromStorageValue("dark"))
        assertEquals(MeltypeThemeMode.SYSTEM, MeltypeThemeMode.fromStorageValue("system"))
    }

    @Test fun explicitModesIgnoreSystemAppearance() {
        assertEquals(
            MeltypeTheme.resolve(MeltypeThemeMode.LIGHT, systemIsDark = true),
            MeltypeTheme.resolve(MeltypeThemeMode.LIGHT, systemIsDark = false),
        )
        assertEquals(
            MeltypeTheme.resolve(MeltypeThemeMode.DARK, systemIsDark = true),
            MeltypeTheme.resolve(MeltypeThemeMode.DARK, systemIsDark = false),
        )
    }

    @Test fun systemModeFollowsSystemNightSetting() {
        val light = MeltypeTheme.resolve(MeltypeThemeMode.SYSTEM, systemIsDark = false)
        val dark = MeltypeTheme.resolve(MeltypeThemeMode.SYSTEM, systemIsDark = true)

        assertEquals(0xFFF2F2F2.toInt(), light.background)
        assertEquals(0xFF121212.toInt(), dark.background)
        assertNotEquals(light.key, dark.key)
        assertNotEquals(light.text, dark.text)
    }
}
