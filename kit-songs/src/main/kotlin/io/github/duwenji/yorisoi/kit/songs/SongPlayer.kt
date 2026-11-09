package io.github.duwenji.yorisoi.kit.songs

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Plays a song on a walk or ride (platform REQ-NEARBY-TALK F-22, F-25, F-26): softer than the voice,
 * not over another app's music or a call, and stopped within a second when the companion has
 * something else to say.
 */
class SongPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** The song playing, and how to end its [play] call. */
    private class Playing(val player: MediaPlayer, val finish: (Boolean) -> Unit)

    @Volatile
    private var playing: Playing? = null

    val isPlaying: Boolean get() = playing != null

    /** Whether a song may start: no other app is playing music and no call is on (F-26). */
    fun mayPlay(): Boolean = !audio.isMusicActive && audio.mode == AudioManager.MODE_NORMAL

    /**
     * Plays [file] to its end, or until [stop]. False when it couldn't start (no focus, or the file
     * can't be played). Takes the audio focus for its length, letting others duck under it.
     */
    suspend fun play(file: File): Boolean {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val p = MediaPlayer()
                    var ended = false
                    val finish = { started: Boolean ->
                        if (!ended) {
                            ended = true
                            if (playing?.player === p) playing = null
                            p.release()
                            if (cont.isActive) cont.resume(started)
                        }
                    }
                    playing = Playing(p, finish)
                    try {
                        p.setAudioAttributes(attributes)
                        p.setDataSource(file.path)
                        p.setVolume(VOLUME, VOLUME)
                        p.setOnCompletionListener { finish(true) }
                        p.setOnErrorListener { _, _, _ -> finish(false); true }
                        p.setOnPreparedListener { it.start() }
                        p.prepareAsync()
                    } catch (e: Exception) {
                        finish(false)
                    }
                    cont.invokeOnCancellation { finish(false) }
                }
            }
        } finally {
            audio.abandonAudioFocusRequest(focus)
        }
    }

    /** Fades the song out over [fadeMs] and stops it (F-25); nothing when none is playing. */
    suspend fun stop(fadeMs: Long = FADE_MS) {
        val now = playing ?: return
        withContext(Dispatchers.Main) {
            val steps = 10
            for (i in steps - 1 downTo 0) {
                if (playing !== now) return@withContext
                val v = VOLUME * i / steps
                try {
                    now.player.setVolume(v, v)
                } catch (e: IllegalStateException) {
                    break
                }
                delay(fadeMs / steps)
            }
            // The completion listener isn't called after a stop: the play() call ends here.
            now.finish(true)
        }
    }

    companion object {
        /** Under the voice, which plays at full volume (F-22). */
        const val VOLUME = 0.6f
        const val FADE_MS = 1_000L
    }
}
