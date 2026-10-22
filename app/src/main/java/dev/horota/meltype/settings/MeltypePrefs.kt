// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.content.Context
import android.content.SharedPreferences
import dev.horota.meltype.input.BindingOptions
import dev.horota.meltype.input.EditorPolicy
import java.io.File

/** Android 版だけの設定 (SharedPreferences)。判定・変換の細かい設定は本家と同じ config.json にある。 */
class MeltypePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("meltype", Context.MODE_PRIVATE)

    var codeApps: Set<String>
        get() = prefs.getStringSet(KEY_CODE_APPS, null)?.toSet() ?: EditorPolicy.DEFAULT_CODE_APPS
        set(value) = prefs.edit().putStringSet(KEY_CODE_APPS, value).apply()

    var shiftSpaceToggles: Boolean
        get() = prefs.getBoolean(KEY_SHIFT_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHIFT_SPACE, value).apply()

    var ctrlSpaceToggles: Boolean
        get() = prefs.getBoolean(KEY_CTRL_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_CTRL_SPACE, value).apply()

    var dollarKeyToggles: Boolean
        get() = prefs.getBoolean(KEY_DOLLAR_TOGGLE, true)
        set(value) = prefs.edit().putBoolean(KEY_DOLLAR_TOGGLE, value).apply()

    var themeMode: MeltypeThemeMode
        get() = MeltypeThemeMode.fromStorageValue(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.storageValue).apply()

    fun registerThemeChangeListener(listener: (MeltypeThemeMode) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME_MODE) listener(themeMode)
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        return preferenceListener
    }

    fun unregisterThemeChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    val bindingOptions: BindingOptions
        get() = BindingOptions(shiftSpaceToggles, ctrlSpaceToggles, dollarKeyToggles)

    companion object {
        private const val KEY_CODE_APPS = "code_apps"
        private const val KEY_SHIFT_SPACE = "shift_space_toggles"
        private const val KEY_CTRL_SPACE = "ctrl_space_toggles"
        private const val KEY_DOLLAR_TOGGLE = "dollar_key_toggles"
        private const val KEY_THEME_MODE = "theme_mode"

        /** 本家の MELTYPE_DATA_DIR に渡す場所 (設定・学習・ユーザー辞書・ログ)。 */
        fun dataDirectory(context: Context): File = File(context.filesDir, "Meltype")
    }
}
