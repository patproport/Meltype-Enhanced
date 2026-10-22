// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.horota.meltype.settings.MeltypeColors
import dev.horota.meltype.settings.MeltypeTheme
import dev.horota.meltype.settings.MeltypeThemeMode
import dev.horota.meltype.settings.SymLayerStore

/** Compact physical-QWERTY guide for the Q25 SYM pages and their editor. */
class SymLayerView(
    context: Context,
    private val onPage: (SymLayerStore.Page?) -> Unit,
    private val onCharacter: (String) -> Unit,
    private val onEditKey: ((Int, String) -> Unit)? = null,
    private val showTabs: Boolean = true,
    private val keyHeightDp: Int = 56,
) : LinearLayout(context) {
    private val keyRows = mutableListOf<LinearLayout>()
    private var colors = MeltypeTheme.resolve(context, MeltypeThemeMode.LIGHT)
    private var currentPage: SymLayerStore.Page? = null
    private var currentMappings: Map<Int, String> = emptyMap()
    private val sidePadding = dp(8)
    private val keyGap = dp(4)
    private val keyHeight = dp(keyHeightDp)

    init {
        orientation = VERTICAL
        setBackgroundColor(colors.background)
        setPadding(sidePadding, 0, sidePadding, dp(4))
        visibility = View.GONE
    }

    fun show(page: SymLayerStore.Page?, mappings: Map<Int, String> = emptyMap()) {
        currentPage = page
        currentMappings = mappings.toMap()
        removeAllViews()
        keyRows.clear()
        visibility = if (page == null) View.GONE else View.VISIBLE
        if (page == null) return

        if (showTabs) {
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                addView(tab("絵文字", page == SymLayerStore.Page.EMOJI) { onPage(SymLayerStore.Page.EMOJI) }, tabParams())
                addView(tab("特殊文字", page == SymLayerStore.Page.SYMBOLS) { onPage(SymLayerStore.Page.SYMBOLS) }, tabParams())
                addView(tab("閉じる", false) { onPage(null) }, tabParams())
            })
        }

        for ((rowIndex, letters) in SymLayerStore.ROWS.withIndex()) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (rowIndex == 2) row.addView(View(context)) // Left utility-key slot.
            for (letter in letters) {
                val code = SymLayerStore.keyCode(letter)
                val character = mappings[code].orEmpty()
                row.addView(key(letter, character, page) {
                    if (onEditKey != null) onEditKey.invoke(code, character)
                    else if (character.isNotEmpty()) onCharacter(character)
                })
            }
            if (rowIndex == 2) repeat(2) { row.addView(View(context)) } // Right utility/empty slots.
            keyRows.add(row)
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, keyHeight).apply {
                if (rowIndex != SymLayerStore.ROWS.lastIndex) bottomMargin = keyGap
            })
        }
    }

    fun applyTheme(colors: MeltypeColors) {
        this.colors = colors
        setBackgroundColor(colors.background)
        show(currentPage, currentMappings)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val keyWidth = SymLayerGeometry.keyWidth(width, sidePadding, keyGap)
        for (row in keyRows) {
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                val margin = if (index < row.childCount - 1) keyGap else 0
                val params = child.layoutParams as LayoutParams
                if (params.width != keyWidth || params.height != keyHeight || params.marginEnd != margin) {
                    child.layoutParams = LayoutParams(keyWidth, keyHeight).apply {
                        marginEnd = margin
                    }
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun key(letter: Char, character: String, page: SymLayerStore.Page, action: () -> Unit): View =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(this@SymLayerView.colors.key)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), this@SymLayerView.colors.border)
            }
            contentDescription = if (character.isEmpty()) "$letter: 未割り当て" else "$letter: $character"
            isEnabled = character.isNotEmpty() || onEditKey != null
            setOnClickListener { action() }
            addView(TextView(context).apply {
                text = character
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                setTextColor(colors.text)
                setAutoSizeTextTypeUniformWithConfiguration(
                    12, if (page == SymLayerStore.Page.EMOJI) 30 else 26, 2, TypedValue.COMPLEX_UNIT_SP
                )
                if (page == SymLayerStore.Page.SYMBOLS) setTypeface(null, Typeface.BOLD)
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(TextView(context).apply {
                text = letter.toString()
                includeFontPadding = false
                setTextColor(colors.secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(2)
                bottomMargin = dp(2)
            })
        }

    private fun tab(label: String, selected: Boolean, action: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(if (selected) colors.selectedTabText else colors.secondaryText)
        isSelected = selected
        if (selected) setBackgroundColor(colors.selectedTab)
        setOnClickListener { action() }
    }

    private fun tabParams() = LayoutParams(0, dp(28), 1f)

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
