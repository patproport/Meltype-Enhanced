// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymLayerGeometryTest {
    @Test fun tenKeysFitInsideQ25Width() {
        val width = 720
        val sidePadding = 16
        val gap = 8
        val keyWidth = SymLayerGeometry.keyWidth(width, sidePadding, gap)

        assertEquals(61, keyWidth)
        assertTrue(SymLayerGeometry.rowWidth(10, keyWidth, gap) <= width - 2 * sidePadding)
    }

    @Test fun shorterRowsKeepTheSameKeyWidth() {
        val width = 320
        val sidePadding = 8
        val gap = 4
        val keyWidth = SymLayerGeometry.keyWidth(width, sidePadding, gap)

        assertEquals(26, keyWidth)
        assertTrue(SymLayerGeometry.rowWidth(9, keyWidth, gap) <
            SymLayerGeometry.rowWidth(10, keyWidth, gap))
        assertEquals(SymLayerGeometry.rowWidth(10, keyWidth, gap),
            SymLayerGeometry.rowWidth(1 + 7 + 2, keyWidth, gap))
    }
}
