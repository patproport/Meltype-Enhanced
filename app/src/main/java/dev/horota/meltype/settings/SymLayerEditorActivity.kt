// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.horota.meltype.ime.SymLayerView

/** Edit a SYM page by tapping its physical-key position. */
class SymLayerEditorActivity : Activity() {
    private lateinit var store: SymLayerStore
    private lateinit var page: SymLayerStore.Page
    private lateinit var grid: SymLayerView
    private val assignments = mutableMapOf<Int, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SymLayerStore(this)
        page = SymLayerStore.Page.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PAGE) }
            ?: SymLayerStore.Page.SYMBOLS
        assignments.putAll(store.mappings(page))
        title = "${page.title}レイヤーを編集"

        grid = SymLayerView(
            this,
            onPage = {},
            onCharacter = {},
            onEditKey = ::editKey,
            showTabs = false,
            keyHeightDp = 48,
        )
        grid.show(page, assignments)
        val colors = MeltypeTheme.resolve(this, MeltypePrefs(this).themeMode)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(TextView(this).apply {
            text = "キーをタップして割り当てを編集"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@SymLayerEditorActivity).apply {
                text = "保存"
                setOnClickListener {
                    store.save(page, assignments)
                    Toast.makeText(this@SymLayerEditorActivity, "保存しました", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@SymLayerEditorActivity).apply {
                text = "初期設定に戻す"
                setOnClickListener {
                    val colors = MeltypeTheme.resolve(this@SymLayerEditorActivity, MeltypePrefs(this@SymLayerEditorActivity).themeMode)
                    val dialog = AlertDialog.Builder(this@SymLayerEditorActivity)
                        .setMessage("${page.title}の割り当てを初期設定に戻しますか？")
                        .setPositiveButton("戻す") { _, _ ->
                            store.reset(page)
                            assignments.clear()
                            assignments.putAll(store.mappings(page))
                            grid.show(page, assignments)
                            Toast.makeText(this@SymLayerEditorActivity, "初期設定に戻しました", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("キャンセル", null)
                        .create()
                    dialog.setOnShowListener {
                        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.surface))
                        MeltypeTheme.applyDialogBackground(dialog.window?.decorView, colors)
                    }
                    dialog.show()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        val scroll = ScrollView(this).apply {
            setBackgroundColor(colors.background)
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
        root.setBackgroundColor(colors.background)
        MeltypeTheme.applyToScreen(root, colors)
        grid.applyTheme(colors)
    }

    private fun editKey(code: Int, current: String) {
        val letter = 'A' + (code - android.view.KeyEvent.KEYCODE_A)
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            setText(current)
            selectAll()
            hint = "$letter の文字（空欄で無効）"
        }
        val dialogContent = LinearLayout(this).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(field, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        val colors = MeltypeTheme.resolve(this, MeltypePrefs(this).themeMode)
        val dialog = AlertDialog.Builder(this)
            .setTitle("$letter の割り当て")
            .setView(dialogContent)
            .setPositiveButton("設定") { _, _ ->
                val value = field.text.toString()
                if (value.isEmpty()) assignments.remove(code) else assignments[code] = value
                grid.show(page, assignments)
            }
            .setNegativeButton("キャンセル", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.surface))
            MeltypeTheme.applyDialogBackground(dialog.window?.decorView, colors)
            MeltypeTheme.applyToScreen(dialogContent, colors)
        }
        dialog.show()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_PAGE = "sym_page"
    }
}
