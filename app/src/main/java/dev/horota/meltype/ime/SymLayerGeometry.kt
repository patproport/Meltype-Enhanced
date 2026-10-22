// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

/** The ten-key QWERTY row determines the width of every SYM key. */
internal object SymLayerGeometry {
    fun keyWidth(availableWidth: Int, sidePadding: Int, gap: Int): Int =
        ((availableWidth - 2 * sidePadding - 9 * gap) / 10).coerceAtLeast(1)

    fun rowWidth(slots: Int, keyWidth: Int, gap: Int): Int =
        slots * keyWidth + (slots - 1).coerceAtLeast(0) * gap
}
