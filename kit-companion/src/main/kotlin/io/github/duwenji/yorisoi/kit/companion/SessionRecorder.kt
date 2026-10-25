package io.github.duwenji.yorisoi.kit.companion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A detailed record of each session (a walk, a ride) taken in developer mode, for reviewing
 * afterwards when the companion talked and why, what the AI was asked and wrote, and how the
 * searches went. One JSON object per line in `<dir>/<session id>.jsonl`, kept on the device for
 * the last [MAX_SESSIONS] sessions; nothing is sent anywhere. It is read back only by the app's export.
 * Plain JVM; the app gives org.json (Android has it).
 *
 * @param startType the type of the first event, e.g. `walk_start`, as the app's analysis reads it
 * @param endType the type of the last event
 * @param onWriteError a line couldn't be written (the record goes on)
 */
class SessionRecorder(
    private val dir: File,
    private val startType: String = "start",
    private val endType: String = "end",
    private val clock: () -> Long = System::currentTimeMillis,
    // One writer, so lines keep their order and reads see every line written before them.
    private val writer: ExecutorService = Executors.newSingleThreadExecutor(),
    private val onWriteError: (IOException) -> Unit = {},
) {
    @Volatile
    private var file: File? = null

    /** Whether a session is being recorded. */
    val recording: Boolean get() = file != null

    /** Starts recording the session [id], with [fields] describing how it was set up. */
    fun start(id: Long, fields: List<Pair<String, Any?>>) {
        val f = File(dir, "$id$SUFFIX")
        writer.execute {
            dir.mkdirs()
            prune(keep = MAX_SESSIONS - 1)
        }
        file = f
        event(startType, fields)
    }

    /** Adds an event of [type] to the session being recorded; does nothing when none is. */
    fun event(type: String, vararg fields: Pair<String, Any?>) = event(type, fields.asList())

    fun event(type: String, fields: List<Pair<String, Any?>>) {
        val f = file ?: return
        val o = JSONObject().put("t", clock()).put("type", type)
        fields.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        val line = o.toString()
        writer.execute {
            try {
                f.appendText(line + "\n")
            } catch (e: IOException) {
                onWriteError(e)
            }
        }
    }

    /** Ends the record of [id]; a later session already being recorded is left alone. */
    fun finish(id: Long) {
        if (file?.name != "$id$SUFFIX") return
        event(endType)
        file = null
    }

    /** The sessions recorded, newest first. */
    fun ids(): List<Long> = writer.submit<List<Long>> {
        dir.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty().mapNotNull { it.name.removeSuffix(SUFFIX).toLongOrNull() }.sortedDescending()
    }.get()

    /** The events recorded for [id], oldest first; empty when it wasn't recorded. */
    fun read(id: Long): JSONArray = writer.submit<JSONArray> {
        val f = File(dir, "$id$SUFFIX")
        JSONArray().apply {
            if (f.exists()) {
                f.forEachLine { line ->
                    // A line cut short (the app was killed mid-write) is skipped, not the whole session.
                    if (line.isNotBlank()) runCatching { JSONObject(line) }.onSuccess { put(it) }
                }
            }
        }
    }.get()

    fun clear() {
        writer.submit { dir.listFiles()?.forEach { it.delete() } }.get()
    }

    private fun prune(keep: Int) {
        dir.listFiles { f -> f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.name.removeSuffix(SUFFIX).toLongOrNull() ?: 0L }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    companion object {
        private const val SUFFIX = ".jsonl"
        const val MAX_SESSIONS = 30
    }
}
