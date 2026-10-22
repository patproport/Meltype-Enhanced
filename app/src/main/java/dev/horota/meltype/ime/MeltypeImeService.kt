// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import dev.horota.meltype.core.CoreBridge
import dev.horota.meltype.core.CoreOpenResult
import dev.horota.meltype.input.EditorPolicy
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.settings.MeltypePrefs
import dev.horota.meltype.settings.MeltypeTheme
import dev.horota.meltype.settings.SymLayerStore
import java.io.File

/**
 * 物理キーボード中心。通常は候補バーだけを出し、SYM 入力時はキーグリッドも出す。
 * Android 11 以降は、表示を頼まれていない IME の窓は見えないので、候補バーを入力用の窓 (input view) として出し、
 * 文字を打ち始めたら自分で表示を頼む (requestShowSelf)。
 */
class MeltypeImeService : InputMethodService() {
    private lateinit var prefs: MeltypePrefs
    private lateinit var bar: CandidateBarView
    private lateinit var controller: ImeController
    private lateinit var layerStore: SymLayerStore
    private lateinit var layerView: SymLayerView
    private lateinit var inputPanel: LinearLayout
    private var layerPage: SymLayerStore.Page? = null
    /** Q25の右AltをSYMとして扱うデバイスID。ALT修飾の残留を通常入力へ渡さない。 */
    private var symAltDeviceId: Int? = null
    private val consumedLayerKeys = mutableSetOf<Int>()
    private val q25RightShiftCtrl = Q25RightShiftCtrl()
    private var themeChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private data class PendingShortcut(val connection: InputConnection, val down: KeyEvent)
    private val pendingQ25Shortcuts = mutableMapOf<Int, PendingShortcut>()
    private var core: CoreBridge? = null

    override fun onCreate() {
        super.onCreate()
        prefs = MeltypePrefs(this)
        layerStore = SymLayerStore(this)
        bar = CandidateBarView(
            this,
            onSelect = { index -> controller.selectCandidate(index) },
            onConvert = { target -> controller.convert(target) },
        )
        layerView = SymLayerView(this, onPage = ::setLayerPage, onCharacter = ::insertMappedCharacter)
        applyCurrentTheme()
        themeChangeListener = prefs.registerThemeChangeListener { applyCurrentTheme() }
        inputPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(layerView)
        }
        val opened = CoreBridge.open(
            MeltypePrefs.dataDirectory(this),
            File(applicationInfo.nativeLibraryDir, "libmozc_helper.so"),
        )
        when (opened) {
            is CoreOpenResult.Ready -> {
                core = opened.core
                bar.setWarning(if (opened.mozcAvailable) null else "漢字変換を使えません")
            }
            is CoreOpenResult.Failed -> bar.setWarning("Meltype を初期化できませんでした")
        }
        controller = ImeController(core, InputConnectionSink { currentInputConnection }, bar, bar)
    }

    override fun onCreateInputView(): View {
        (inputPanel.parent as? ViewGroup)?.removeView(inputPanel)
        return inputPanel
    }

    /** 物理キーボードがあっても候補バーを出す (既定では物理キーボードがあると出さない)。パスワード欄などでは出さない。 */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return !::controller.isInitialized || controller.showsBar
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        applyCurrentTheme()
        releaseQ25Shortcuts()
        controller.options = prefs.bindingOptions
        controller.startInput(EditorPolicy.of(attribute.inputType, attribute.packageName, prefs.codeApps, attribute.imeOptions))
        consumedLayerKeys.clear()
        setLayerPage(null)
        updateInputViewShown()
    }

    override fun onFinishInput() {
        releaseQ25Shortcuts()
        setLayerPage(null)
        consumedLayerKeys.clear()
        controller.finishInput()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.selectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onDestroy() {
        releaseQ25Shortcuts()
        themeChangeListener?.let(prefs::unregisterThemeChangeListener)
        themeChangeListener = null
        controller.shutdown()
        core?.close()
        core = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::prefs.isInitialized && ::bar.isInitialized && ::layerView.isInitialized) applyCurrentTheme()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isSymTrigger = keyCode == KeyEvent.KEYCODE_SYM || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
        if (isSymTrigger && currentInputConnection != null &&
            controller.showsBar && controller.mode != InputMode.PAUSED
        ) {
            if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT) symAltDeviceId = event.deviceId
            if (event.repeatCount == 0) {
                val next = when (layerPage) {
                    null -> SymLayerStore.Page.EMOJI
                    SymLayerStore.Page.EMOJI -> SymLayerStore.Page.SYMBOLS
                    SymLayerStore.Page.SYMBOLS -> null
                }
                setLayerPage(next)
            }
            consumedLayerKeys += keyCode
            if (layerPage != null && !isInputViewShown) requestShowSelf(0)
            return true
        }
        val deviceName = InputDevice.getDevice(event.deviceId)?.name
        val connection = currentInputConnection
        when (q25RightShiftCtrl.onDown(
            keyCode, event.scanCode, event.deviceId, deviceName,
            connection != null && controller.showsBar && controller.mode != InputMode.PAUSED,
            event.repeatCount,
        )) {
            Q25RightShiftCtrl.Down.CONSUME -> return true
            Q25RightShiftCtrl.Down.SEND -> {
                val target = connection ?: return true
                val selection = if (keyCode == KeyEvent.KEYCODE_C || keyCode == KeyEvent.KEYCODE_X) {
                    target.getExtractedText(ExtractedTextRequest(), 0)
                } else null
                val committed = controller.prepareForExternalShortcut()
                if (committed && selection != null && selection.selectionStart >= 0 &&
                    selection.selectionEnd > selection.selectionStart
                ) {
                    target.setSelection(selection.selectionStart, selection.selectionEnd)
                }
                pendingQ25Shortcuts[keyCode] = PendingShortcut(target, event)
                target.sendKeyEvent(q25CtrlEvent(event))
                return true
            }
            Q25RightShiftCtrl.Down.PASS -> Unit
        }
        if (layerPage != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                setLayerPage(null)
                consumedLayerKeys += keyCode
                return true
            }
            val metaState = effectiveMetaState(event)
            val shortcut = metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON) != 0
            if (keyCode in SymLayerStore.LETTER_CODES && !shortcut) {
                layerStore.mappings(layerPage!!)[keyCode]?.let(controller::insertExternalText)
                consumedLayerKeys += keyCode
                return true
            }
        }
        val metaState = effectiveMetaState(event)
        val key = KeyInput(
            keyCode, event.getUnicodeChar(metaState), event.getUnicodeChar(0), metaState,
            event.scanCode, deviceName,
        )
        if (controller.keyDown(key, allowDollarToggle = layerPage == null, isRepeat = event.repeatCount > 0)) {
            // 入力欄をタップせずに打ち始めたときは、まだ窓が出ていない
            if (!isInputViewShown) requestShowSelf(0)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setLayerPage(page: SymLayerStore.Page?) {
        layerPage = page
        layerView.show(page, page?.let(layerStore::mappings) ?: emptyMap())
    }

    private fun applyCurrentTheme() {
        val colors = MeltypeTheme.resolve(this, prefs.themeMode)
        bar.applyTheme(colors)
        layerView.applyTheme(colors)
    }

    /** Androidが右Alt由来のALT修飾を報告しても、Q25のSYMとして処理したデバイスでは除去する。左Altは維持。 */
    private fun effectiveMetaState(event: KeyEvent): Int {
        val isQ25SymDevice = symAltDeviceId == event.deviceId
        val leftAltDown = event.metaState and KeyEvent.META_ALT_LEFT_ON != 0
        return if (isQ25SymDevice && !leftAltDown) {
            event.metaState and KeyEvent.META_ALT_ON.inv() and KeyEvent.META_ALT_RIGHT_ON.inv()
        } else {
            event.metaState
        }
    }

    private fun insertMappedCharacter(text: String) {
        controller.insertExternalText(text)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (q25RightShiftCtrl.onUp(keyCode, event.scanCode, event.deviceId)) {
            Q25RightShiftCtrl.Up.CONSUME -> return true
            Q25RightShiftCtrl.Up.SEND -> {
                pendingQ25Shortcuts.remove(keyCode)?.let { pending ->
                    pending.connection.sendKeyEvent(q25CtrlEvent(event, effectiveMetaState(pending.down)))
                }
                return true
            }
            Q25RightShiftCtrl.Up.PASS -> Unit
        }
        return consumedLayerKeys.remove(keyCode) || controller.keyUp(keyCode) || super.onKeyUp(keyCode, event)
    }

    private fun q25CtrlEvent(event: KeyEvent, metaState: Int = effectiveMetaState(event)): KeyEvent = KeyEvent(
        event.downTime, event.eventTime, event.action, event.keyCode, 0,
        Q25RightShiftCtrl.ctrlMetaState(metaState),
        event.deviceId, event.scanCode, event.flags, event.source,
    )

    private fun releaseQ25Shortcuts() {
        for ((keyCode, pending) in pendingQ25Shortcuts) {
            val down = pending.down
            val up = KeyEvent(
                down.downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0,
                Q25RightShiftCtrl.ctrlMetaState(effectiveMetaState(down)),
                down.deviceId, down.scanCode, down.flags, down.source,
            )
            pending.connection.sendKeyEvent(up)
        }
        pendingQ25Shortcuts.clear()
        q25RightShiftCtrl.clear()
    }
}
