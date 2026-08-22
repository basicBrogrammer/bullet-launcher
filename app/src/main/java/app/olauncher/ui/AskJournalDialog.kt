package app.olauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import app.olauncher.R
import app.olauncher.ai.JournalAssistant
import app.olauncher.ai.JournalEngine
import app.olauncher.ai.JournalToolCopy
import app.olauncher.ai.JournalToolExecutor
import app.olauncher.ai.JournalToolResult
import app.olauncher.ai.NanoAvailability
import app.olauncher.data.JournalStore
import app.olauncher.helper.getColorFromAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object AskJournalDialog {

    fun show(
        context: Context,
        scope: CoroutineScope,
        store: JournalStore,
        assistant: JournalAssistant = JournalAssistant(),
        onChanged: () -> Unit,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val host = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val sheet = LayoutInflater.from(context).inflate(R.layout.dialog_ask_journal, host, false)
        host.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        dialog.setContentView(host)
        sheet.setBackgroundColor(context.getColorFromAttr(R.attr.drawerBackgroundColor))

        val closedBottomMargin = dp(context, 28)
        var bottomInset = closedBottomMargin

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.FILL)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.55f
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                y = 0
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        fun applyBottomInset(ime: Int, nav: Int) {
            val next = if (ime > 0) ime else maxOf(nav, closedBottomMargin)
            if (next == bottomInset) return
            bottomInset = next
            sheet.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = bottomInset
                gravity = Gravity.BOTTOM
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyBottomInset(ime, nav)
            insets
        }
        sheet.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = bottomInset
        }

        val input = sheet.findViewById<EditText>(R.id.askInput)
        val engineStatus = sheet.findViewById<TextView>(R.id.engineStatus)
        val resultView = sheet.findViewById<TextView>(R.id.askResult)
        val downloadButton = sheet.findViewById<TextView>(R.id.downloadButton)
        val cancel = sheet.findViewById<TextView>(R.id.cancelButton)
        val ask = sheet.findViewById<TextView>(R.id.askButton)
        var availability = NanoAvailability.UNAVAILABLE

        fun refreshEngineLabel(usedNano: Boolean = false) {
            engineStatus.text = JournalToolCopy.engineLabel(context, availability, usedNano)
            downloadButton.isVisible = availability == NanoAvailability.DOWNLOADABLE
        }

        fun setBusy(busy: Boolean) {
            ask.isEnabled = !busy
            downloadButton.isEnabled = !busy
            input.isEnabled = !busy
            ask.alpha = if (busy) 0.45f else 1f
        }

        fun runAsk() {
            val text = input.text?.toString().orEmpty()
            if (text.isBlank()) return
            setBusy(true)
            resultView.isVisible = true
            resultView.setText(R.string.ask_journal_thinking)
            scope.launch {
                val contextForAsk = JournalToolExecutor.contextFrom(store)
                val interpretation = assistant.interpret(text, contextForAsk)
                val result = JournalToolExecutor.execute(store, interpretation.tool, contextForAsk)
                resultView.text = JournalToolCopy.describe(context, result)
                refreshEngineLabel(usedNano = interpretation.engine == JournalEngine.NANO)
                when (result) {
                    is JournalToolResult.Added -> {
                        input.text?.clear()
                        onChanged()
                    }
                    is JournalToolResult.Completed -> onChanged()
                    else -> Unit
                }
                setBusy(false)
            }
        }

        scope.launch {
            availability = assistant.availability()
            refreshEngineLabel()
        }

        downloadButton.setOnClickListener {
            setBusy(true)
            engineStatus.setText(R.string.ask_journal_engine_downloading)
            scope.launch {
                val ok = assistant.downloadModel()
                availability = assistant.availability()
                refreshEngineLabel()
                if (!ok && availability != NanoAvailability.AVAILABLE) {
                    resultView.isVisible = true
                    resultView.setText(R.string.ask_journal_download_failed)
                }
                setBusy(false)
            }
        }
        cancel.setOnClickListener { dialog.dismiss() }
        ask.setOnClickListener { runAsk() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                runAsk()
                true
            } else {
                false
            }
        }

        dialog.show()
        input.requestFocus()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
