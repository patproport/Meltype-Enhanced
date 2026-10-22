// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.content.Context
import android.view.KeyEvent
import org.json.JSONObject

/** Pastiera のGPLv3標準割り当てを読み、Meltype固有の編集内容を保存する。 */
class SymLayerStore(private val context: Context) {
    enum class Page(val title: String, val asset: String, val preference: String) {
        SYMBOLS("特殊文字", "pastiera/sym_key_mappings_page2.json", "sym_symbols_custom"),
        EMOJI("絵文字", "pastiera/sym_key_mappings.json", "sym_emoji_custom"),
    }

    private val preferences = context.getSharedPreferences("meltype", Context.MODE_PRIVATE)
    private val defaults = mutableMapOf<Page, Map<Int, String>>()

    fun mappings(page: Page): Map<Int, String> {
        val original = defaults.getOrPut(page) {
            context.assets.open(page.asset).bufferedReader().use { parse(it.readText()) }
        }
        val custom = preferences.getString(page.preference, null) ?: return original
        return try {
            parse(custom)
        } catch (_: org.json.JSONException) {
            original
        }
    }

    fun save(page: Page, mappings: Map<Int, String>) {
        val encoded = JSONObject()
        val keys = JSONObject()
        for ((keyCode, value) in mappings) {
            if (keyCode in LETTER_CODES && value.isNotEmpty()) {
                keys.put(KeyEvent.keyCodeToString(keyCode), value)
            }
        }
        encoded.put("mappings", keys)
        preferences.edit().putString(page.preference, encoded.toString()).apply()
    }

    fun reset(page: Page) {
        preferences.edit().remove(page.preference).apply()
    }

    private fun parse(json: String): Map<Int, String> {
        val entries = JSONObject(json).getJSONObject("mappings")
        val result = mutableMapOf<Int, String>()
        for (name in entries.keys()) {
            val code = KeyEvent.keyCodeFromString(name)
            if (code in LETTER_CODES) result[code] = entries.getString(name)
        }
        return result
    }

    companion object {
        val ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
        val LETTER_CODES: Set<Int> = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toSet()
        fun keyCode(letter: Char): Int = KeyEvent.KEYCODE_A + (letter.uppercaseChar() - 'A')
    }
}