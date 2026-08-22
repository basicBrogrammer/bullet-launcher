package app.olauncher.ai

import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.collect

enum class NanoAvailability {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

/**
 * Thin wrapper around ML Kit GenAI Prompt API (Gemini Nano via AICore).
 *
 * Prompt API has no tool / function-calling surface — it returns text — so
 * this client asks for a single JSON tool call and [JournalToolParser] reads it.
 */
class NanoJournalClient(
    private val modelProvider: () -> GenerativeModel = { Generation.getClient() },
) {
    private var model: GenerativeModel? = null

    suspend fun availability(): NanoAvailability {
        if (Build.VERSION.SDK_INT < 34) return NanoAvailability.UNAVAILABLE
        return try {
            when (client().checkStatus()) {
                FeatureStatus.AVAILABLE -> NanoAvailability.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> NanoAvailability.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> NanoAvailability.DOWNLOADING
                else -> NanoAvailability.UNAVAILABLE
            }
        } catch (error: Exception) {
            Log.i(TAG, "Nano unavailable: ${error.message}")
            NanoAvailability.UNAVAILABLE
        }
    }

    suspend fun downloadIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return try {
            val generative = client()
            when (generative.checkStatus()) {
                FeatureStatus.AVAILABLE -> true
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    var completed = false
                    generative.download().collect { status ->
                        when (status) {
                            is DownloadStatus.DownloadCompleted -> completed = true
                            is DownloadStatus.DownloadFailed -> {
                                Log.w(TAG, "Nano download failed: ${status.e.message}")
                                completed = false
                            }
                            else -> Unit
                        }
                    }
                    completed
                }
                else -> false
            }
        } catch (error: Exception) {
            Log.w(TAG, "Nano download failed: ${error.message}")
            false
        }
    }

    suspend fun interpret(input: String, context: JournalAskContext): JournalTool? {
        if (Build.VERSION.SDK_INT < 34) return null
        val prompt = buildPrompt(input, context)
        return try {
            val response = client().generateContent(prompt)
            val text = response.candidates.firstOrNull()?.text.orEmpty()
            JournalToolParser.parseModelOutput(text, context)
        } catch (error: Exception) {
            Log.w(TAG, "Nano interpret failed: ${error.message}")
            null
        }
    }

    private fun client(): GenerativeModel {
        val existing = model
        if (existing != null) return existing
        val created = modelProvider()
        model = created
        return created
    }

    companion object {
        private const val TAG = "NanoJournal"

        fun buildPrompt(input: String, context: JournalAskContext): String {
            val open = context.openTasks.take(12).joinToString("; ") { it.text }
            val openLine = if (open.isEmpty()) "(none)" else open
            return """
                Convert the user command into one JSON object. No markdown. No extra keys.
                Tools:
                {"tool":"add_task","text":"...","date":"today|tomorrow|yyyy-MM-dd|unscheduled","priority":false}
                {"tool":"complete_task","query":"..."}
                {"tool":"add_event","text":"...","date":"...","time":"HH:mm"}
                {"tool":"add_note","text":"...","date":"..."}
                {"tool":"list_tasks"}
                {"tool":"reply","text":"..."}
                Today is ${context.todayKey}. Open tasks: $openLine
                User: $input
            """.trimIndent()
        }
    }
}
