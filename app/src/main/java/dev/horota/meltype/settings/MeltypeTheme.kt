// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView

enum class MeltypeThemeMode(val storageValue: String, val title: String) {
    LIGHT("light", "ライト"),
    DARK("dark", "ダーク"),
    SYSTEM("system", "システムに合わせる");

    companion object {
        fun fromStorageValue(value: String?): MeltypeThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: LIGHT
    }
}

data class MeltypeColors(
    val background: Int,
    val surface: Int,
    val key: Int,
    val border: Int,
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    val selectedTab: Int,
    val selectedTabText: Int,
    val selectedCandidate: Int,
    val selectedCandidateText: Int,
    val warning: Int,
    val controlSurface: Int,
    val controlText: Int,
)

/** Shared colors for the IME, SYM editor, and app settings. */
object MeltypeTheme {
    fun resolve(context: Context, mode: MeltypeThemeMode): MeltypeColors {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return resolve(mode, nightMask == Configuration.UI_MODE_NIGHT_YES)
    }

    fun resolve(mode: MeltypeThemeMode, systemIsDark: Boolean): MeltypeColors {
        val dark = when (mode) {
            MeltypeThemeMode.LIGHT -> false
            MeltypeThemeMode.DARK -> true
            MeltypeThemeMode.SYSTEM -> systemIsDark
        }
        return if (dark) DARK_COLORS else LIGHT_COLORS
    }

    /** Apply common text and control colors to settings/editor views without changing their layout. */
    fun applyToScreen(view: View, colors: MeltypeColors) {
        when (view) {
            is Switch -> {
                view.setTextColor(colors.text)
                view.thumbTintList = ColorStateList.valueOf(colors.accent)
                view.trackTintList = ColorStateList.valueOf(colors.border)
            }
            is EditText -> {
                view.setTextColor(colors.text)
                view.setHintTextColor(colors.secondaryText)
                view.backgroundTintList = ColorStateList.valueOf(colors.border)
            }
            is Button -> {
                view.setTextColor(colors.controlText)
                view.backgroundTintList = ColorStateList.valueOf(colors.controlSurface)
            }
            is TextView -> view.setTextColor(colors.text)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyToScreen(view.getChildAt(index), colors)
        }
    }

    fun applyDialogBackground(view: View?, colors: MeltypeColors) {
        view?.background = ColorDrawable(colors.surface)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyDialogText(view.getChildAt(index), colors)
        }
    }

    private fun applyDialogText(view: View, colors: MeltypeColors) {
        if (view is TextView) view.setTextColor(colors.text)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyDialogText(view.getChildAt(index), colors)
        }
    }

    private val LIGHT_COLORS = MeltypeColors(
        background = 0xFFF2F2F2.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        key = 0xFFFFFFFF.toInt(),
        border = 0xFFD6D6D6.toInt(),
        text = 0xFF1A1A1A.toInt(),
        secondaryText = 0xFF666666.toInt(),
        accent = 0xFF1A5FB4.toInt(),
        selectedTab = 0xFFE3EDF8.toInt(),
        selectedTabText = 0xFF1A5FB4.toInt(),
        selectedCandidate = 0xFF4CA0FF.toInt(),
        selectedCandidateText = 0xFF101010.toInt(),
        warning = 0xFFC62828.toInt(),
        controlSurface = 0xFFFFFFFF.toInt(),
        controlText = 0xFF1A1A1A.toInt(),
    )

    private val DARK_COLORS = MeltypeColors(
        background = 0xFF121212.toInt(),
        surface = 0xFF1C1C1C.toInt(),
        key = 0xFF2A2A2A.toInt(),
        border = 0xFF555555.toInt(),
        text = 0xFFF2F2F2.toInt(),
        secondaryText = 0xFFBDBDBD.toInt(),
        accent = 0xFF8AB4F8.toInt(),
        selectedTab = 0xFF34495E.toInt(),
        selectedTabText = 0xFFFFFFFF.toInt(),
        selectedCandidate = 0xFF315A89.toInt(),
        selectedCandidateText = 0xFFFFFFFF.toInt(),
        warning = 0xFFFF8A80.toInt(),
        controlSurface = 0xFF2A2A2A.toInt(),
        controlText = 0xFFF2F2F2.toInt(),
    )
}
