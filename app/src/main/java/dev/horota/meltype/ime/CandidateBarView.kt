// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.settings.MeltypeColors
import dev.horota.meltype.settings.MeltypeTheme
import dev.horota.meltype.settings.MeltypeThemeMode

/**
 * 1 行の候補バー (Q25 の 720×720 の画面でも邪魔にならない高さ)。左端に英 / 日の状態、右に候補。
 * 候補をタップすると onSelect(index)。入力中は右端に「カナ」「英数」を出し、タップすると onConvert。
 */
class CandidateBarView(
    context: Context,
    private val onSelect: (Int) -> Unit,
    private val onConvert: (ConvertTarget) -> Unit,
) :
    LinearLayout(context), CandidateDisplay, StatusDisplay {

    private var colors = MeltypeTheme.resolve(context, MeltypeThemeMode.LIGHT)
    private var lastComposition: CompositionView? = null
    private var lastInlineReading = false
    private var currentMode = InputMode.UNAVAILABLE
    private var warningText = ""
    private val modeLabel = label().apply { setTypeface(typeface, Typeface.BOLD) }
    private val warningLabel = label()
    /** 入力欄が未確定の文字を描かないときの読み (Termux など)。 */
    private val readingLabel = label().apply { paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG }
    private val list = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val convertButtons = LinearLayout(context).apply {
        orientation = HORIZONTAL
        visibility = GONE
        addView(button("カナ", ConvertTarget.KATAKANA))
        addView(button("英数", ConvertTarget.ALPHANUMERIC))
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(colors.background)
        minimumHeight = dp(36)
        addView(modeLabel)
        addView(warningLabel)
        addView(readingLabel)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(convertButtons)
    }

    override fun show(view: CompositionView, inlineReading: Boolean) {
        lastComposition = view
        lastInlineReading = inlineReading
        render(view, inlineReading)
    }

    private fun render(view: CompositionView, inlineReading: Boolean) {
        readingLabel.text = if (inlineReading) view.text else ""
        convertButtons.visibility = if (view.text.isNotEmpty()) VISIBLE else GONE
        list.removeAllViews()
        val items = view.candidates.ifEmpty { view.predictions }
        val selected = if (view.candidates.isNotEmpty()) view.selectedIndex else view.selectedPrediction
        items.forEachIndexed { index, text ->
            list.addView(label().apply {
                this.text = text
                if (index == selected) {
                    setBackgroundColor(colors.selectedCandidate)
                    setTextColor(colors.selectedCandidateText)
                }
                if (view.candidates.isNotEmpty()) setOnClickListener { onSelect(index) }
            })
        }
    }

    override fun hide() {
        lastComposition = null
        readingLabel.text = ""
        convertButtons.visibility = GONE
        list.removeAllViews()
    }

    override fun showMode(mode: InputMode) {
        currentMode = mode
        modeLabel.text = when (mode) {
            InputMode.JAPANESE -> "あ"
            InputMode.DIRECT -> "A"
            InputMode.PAUSED -> "⏸"
            InputMode.UNAVAILABLE -> "⚠"
        }
        modeLabel.setTextColor(colors.text)
    }

    /** null で消す。 */
    fun setWarning(text: String?) {
        warningText = text.orEmpty()
        warningLabel.text = warningText
        warningLabel.setTextColor(colors.warning)
    }

    fun applyTheme(colors: MeltypeColors) {
        this.colors = colors
        setBackgroundColor(colors.background)
        readingLabel.setTextColor(colors.text)
        for (index in 0 until convertButtons.childCount) {
            (convertButtons.getChildAt(index) as? TextView)?.setTextColor(colors.accent)
        }
        setWarning(warningText)
        showMode(currentMode)
        lastComposition?.let { render(it, lastInlineReading) }
    }

    private fun button(text: String, target: ConvertTarget) = label().apply {
        this.text = text
        setTextColor(colors.accent)
        setOnClickListener { onConvert(target) }
    }

    private fun label() = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.text)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
