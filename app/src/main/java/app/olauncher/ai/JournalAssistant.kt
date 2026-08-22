package app.olauncher.ai

/**
 * Interprets a natural-language journal command.
 *
 * Prefers on-device Gemini Nano when AICore has the model; otherwise uses
 * the deterministic parser so the same tools still run on unsupported phones.
 */
class JournalAssistant(
    private val nano: NanoJournalClient = NanoJournalClient(),
) {
    suspend fun interpret(input: String, context: JournalAskContext): JournalInterpretation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return JournalInterpretation(JournalTool.Reply(""), JournalEngine.FALLBACK)
        }
        if (nano.availability() == NanoAvailability.AVAILABLE) {
            val fromNano = nano.interpret(trimmed, context)
            if (fromNano != null) {
                return JournalInterpretation(fromNano, JournalEngine.NANO)
            }
        }
        return JournalInterpretation(
            JournalToolParser.parseFallback(trimmed, context),
            JournalEngine.FALLBACK,
        )
    }

    suspend fun availability(): NanoAvailability = nano.availability()

    suspend fun downloadModel(): Boolean = nano.downloadIfNeeded()
}
