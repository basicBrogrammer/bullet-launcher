package app.olauncher.ai

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import app.olauncher.data.JournalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Journal tools exposed to Android assistants (Gemini) via App Functions.
 *
 * These call the same [JournalAppFunctionHandler] used conceptually by Ask
 * journal / Gemini Nano. Register the class from [app.olauncher.BulletLauncherApplication].
 */
class JournalAppFunctions {

    /**
     * Add a bullet-journal task.
     *
     * @param appFunctionContext The execution context.
     * @param text The task title, for example "Buy milk".
     * @param date today, tomorrow, a yyyy-MM-dd day, or unscheduled. Use today if unknown.
     * @param priority true to mark the task as high priority.
     * @return A short confirmation of what was added.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addTask(
        appFunctionContext: AppFunctionContext,
        text: String,
        date: String,
        priority: Boolean,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            throw AppFunctionInvalidArgumentException("text must not be empty")
        }
        handler(appFunctionContext).addTask(text, date, priority)
    }

    /**
     * Complete an open task by name. If several tasks match, nothing is
     * completed and the matching titles are returned so the caller can ask
     * the user which one.
     *
     * @param appFunctionContext The execution context.
     * @param query Words from the task title, for example "milk".
     * @return Confirmation, an already-done notice, or a list of matches.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun completeTask(
        appFunctionContext: AppFunctionContext,
        query: String,
    ): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException("query must not be empty")
        }
        handler(appFunctionContext).completeTask(query)
    }

    /**
     * Add a bullet-journal event. Use this for appointments and meetings, not
     * to-dos.
     *
     * @param appFunctionContext The execution context.
     * @param text The event title, for example "Dentist".
     * @param date today, tomorrow, a yyyy-MM-dd day, or unscheduled.
     * @param time Time of day such as 15:30 or 3:30 PM. Pass an empty string if unknown.
     * @return A short confirmation of what was added.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addEvent(
        appFunctionContext: AppFunctionContext,
        text: String,
        date: String,
        time: String,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            throw AppFunctionInvalidArgumentException("text must not be empty")
        }
        handler(appFunctionContext).addEvent(text, date, time.ifBlank { null })
    }

    /**
     * Add a bullet-journal note or thought.
     *
     * @param appFunctionContext The execution context.
     * @param text The note body.
     * @param date today, tomorrow, a yyyy-MM-dd day, or unscheduled. Use today if unknown.
     * @return A short confirmation of what was added.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addNote(
        appFunctionContext: AppFunctionContext,
        text: String,
        date: String,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            throw AppFunctionInvalidArgumentException("text must not be empty")
        }
        handler(appFunctionContext).addNote(text, date)
    }

    /**
     * List open (incomplete) journal tasks so the assistant can pick one to
     * complete or summarize what is left.
     *
     * @param appFunctionContext The execution context.
     * @return Open task titles, or a notice that none are open.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listOpenTasks(
        appFunctionContext: AppFunctionContext,
    ): String = withContext(Dispatchers.IO) {
        handler(appFunctionContext).listOpenTasks()
    }

    private fun handler(appFunctionContext: AppFunctionContext): JournalAppFunctionHandler {
        val context = appFunctionContext.context
        return JournalAppFunctionHandler(JournalStore(context), context)
    }
}
