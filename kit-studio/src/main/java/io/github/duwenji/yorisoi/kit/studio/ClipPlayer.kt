package io.github.duwenji.yorisoi.kit.studio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 * Placements' audio kept in the app's cache, named by its SHA-256 (API-STUDIO D-22): fetched with
 * the cell list, before the user comes near, and used only when its hash matches.
 */
class ClipCache(private val dir: File, private val http: OkHttpClient = OkHttpClient()) : ClipFetcher {
    override suspend fun fetch(audio: PlacementAudio): File? = withContext(Dispatchers.IO) {
        val file = File(dir, "${audio.sha256}.m4a")
        if (file.exists()) return@withContext file
        if (audio.bytes > MAX_BYTES) return@withContext null
        try {
            val bytes = http.newCall(Request.Builder().url(audio.url).build()).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                res.body?.bytes() ?: return@withContext null
            }
            if (sha256(bytes) != audio.sha256) return@withContext null
            dir.mkdirs()
            val tmp = File(dir, "${audio.sha256}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) return@withContext null
            trim()
            file
        } catch (e: IOException) {
            null
        }
    }

    /** Keeps the newest clips within [MAX_TOTAL]. */
    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".m4a") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        for (f in files) {
            total += f.length()
            if (total > MAX_TOTAL) f.delete()
        }
    }

    companion object {
        const val MAX_BYTES = 512L * 1024
        private const val MAX_TOTAL = 20L * 1024 * 1024

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** Plays a placement's audio once the companion has finished speaking (REQ-STUDIO D-10). */
class ClipPlayer(private val speaking: StateFlow<Boolean>) {
    /** Waits for the speech to end, then plays [file] to its end. A file that can't be played is skipped. */
    suspend fun play(file: File) {
        speaking.first { !it }
        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer()
            val done = {
                player.release()
                if (cont.isActive) cont.resume(Unit)
            }
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                player.setDataSource(file.path)
                player.setOnCompletionListener { done() }
                player.setOnErrorListener { _, _, _ -> done(); true }
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            } catch (e: Exception) {
                done()
            }
            cont.invokeOnCancellation { player.release() }
        }
    }
}
