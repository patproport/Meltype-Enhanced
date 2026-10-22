// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** v0.1 の最小限の設定。細かい設定は本家と同じ config.json (<filesDir>/Meltype) にある。 */
class SettingsActivity : Activity() {
    private lateinit var prefs: MeltypePrefs
    private lateinit var settingsRoot: LinearLayout
    private lateinit var contentScroll: ScrollView
    private lateinit var themeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = MeltypePrefs(this)
        settingsRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        settingsRoot.addView(heading("外観"))
        themeButton = button(themeButtonLabel()) {}
        themeButton.setOnClickListener { showThemePicker(settingsRoot, contentScroll, themeButton) }
        settingsRoot.addView(themeButton)

        settingsRoot.addView(heading("はじめに"))
        settingsRoot.addView(button("1. Meltype Enhanced を有効にする") { open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        settingsRoot.addView(button("2. Meltype Enhanced を選ぶ") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
        settingsRoot.addView(button("3. 物理キーボードの配列 (JIS キーボードなら「日本語」)") {
            open(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
        })

        settingsRoot.addView(heading("英 / 日の切り替え"))
        settingsRoot.addView(TextView(this).apply { text = "半角/全角キーはいつでも使えます。Ctrl+半角/全角 で一時停止します。" })
        settingsRoot.addView(switch("Shift+Space で切り替える (Q25 向け)", prefs.shiftSpaceToggles) { prefs.shiftSpaceToggles = it })
        settingsRoot.addView(switch("Ctrl+Space で切り替える", prefs.ctrlSpaceToggles) { prefs.ctrlSpaceToggles = it })
        settingsRoot.addView(switch("\$キーで言語切り替え", prefs.dollarKeyToggles) { prefs.dollarKeyToggles = it })

        settingsRoot.addView(heading("SYM レイヤー"))
        settingsRoot.addView(button("特殊文字レイヤーを編集") {
            startActivity(Intent(this, SymLayerEditorActivity::class.java).putExtra(SymLayerEditorActivity.EXTRA_PAGE, SymLayerStore.Page.SYMBOLS.name))
        })
        settingsRoot.addView(button("絵文字レイヤーを編集") {
            startActivity(Intent(this, SymLayerEditorActivity::class.java).putExtra(SymLayerEditorActivity.EXTRA_PAGE, SymLayerStore.Page.EMOJI.name))
        })

        settingsRoot.addView(heading("コード入力モードにするアプリ (1 行に 1 つ)"))
        val codeApps = EditText(this).apply { setText(CodeAppList.format(prefs.codeApps)); minLines = 3 }
        settingsRoot.addView(codeApps)
        settingsRoot.addView(button("保存") {
            prefs.codeApps = CodeAppList.parse(codeApps.text.toString())
            codeApps.setText(CodeAppList.format(prefs.codeApps))
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        })

        settingsRoot.addView(heading("辞書と学習"))
        settingsRoot.addView(button("ユーザー辞書を編集する") { startActivity(Intent(this, UserDictionaryActivity::class.java)) })
        settingsRoot.addView(button("学習データを消す") { confirmClearLearning() })

        settingsRoot.addView(heading("このアプリについて"))
        settingsRoot.addView(button("ライセンス") { showNotices() })

        contentScroll = ScrollView(this).apply {
            setBackgroundColor(MeltypeTheme.resolve(this@SettingsActivity, prefs.themeMode).background)
            addView(settingsRoot)
        }
        setContentView(contentScroll)
        applySettingsTheme(settingsRoot, contentScroll)
    }

    private fun themeButtonLabel() = "テーマ: ${prefs.themeMode.title}"

    private fun showThemePicker(root: LinearLayout, scrollView: ScrollView, themeButton: Button) {
        val modes = MeltypeThemeMode.entries
        val colors = MeltypeTheme.resolve(this, prefs.themeMode)
        val dialog = AlertDialog.Builder(this)
            .setTitle("テーマ")
            .setSingleChoiceItems(modes.map { it.title }.toTypedArray(), modes.indexOf(prefs.themeMode)) { opened, selected ->
                prefs.themeMode = modes[selected]
                themeButton.text = themeButtonLabel()
                applySettingsTheme(root, scrollView)
                opened.dismiss()
            }
            .setNegativeButton("キャンセル", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.surface))
            MeltypeTheme.applyDialogBackground(dialog.window?.decorView, colors)
        }
        dialog.show()
    }

    private fun applySettingsTheme(root: LinearLayout, scrollView: ScrollView) {
        val colors = MeltypeTheme.resolve(this, prefs.themeMode)
        root.setBackgroundColor(colors.background)
        scrollView.setBackgroundColor(colors.background)
        MeltypeTheme.applyToScreen(root, colors)
    }

    private fun confirmClearLearning() {
        val colors = MeltypeTheme.resolve(this, prefs.themeMode)
        val dialog = AlertDialog.Builder(this)
            .setMessage("覚えた変換・英語 / 日本語の判定を消します (ユーザー辞書と設定は残ります)。Meltype を再起動します。")
            .setPositiveButton("消す") { _, _ ->
                LearningData.requestClear(MeltypePrefs.dataDirectory(this))
                // 同じプロセスの IME ごと終了する。次に IME が起動したとき (CoreBridge.open) に消える。
                Process.killProcess(Process.myPid())
            }
            .setNegativeButton("やめる", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.surface))
            MeltypeTheme.applyDialogBackground(dialog.window?.decorView, colors)
        }
        dialog.show()
    }

    private fun showNotices() {
        val text = Notices.compose { name ->
            try {
                assets.open("notices/$name").bufferedReader().use { it.readText() }
            } catch (e: java.io.IOException) {
                null
            }
        }
        val colors = MeltypeTheme.resolve(this, prefs.themeMode)
        val dialog = AlertDialog.Builder(this)
            .setTitle("ライセンス")
            .setView(ScrollView(this).apply {
                addView(TextView(context).apply {
                    this.text = text
                    textSize = 11f
                    setTextIsSelectable(true)
                    setPadding(32, 16, 32, 16)
                })
            })
            .setPositiveButton("閉じる", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.surface))
            MeltypeTheme.applyDialogBackground(dialog.window?.decorView, colors)
        }
        dialog.show()
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 32, 0, 8)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun switch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }
}
