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
 * never during a call, and stopped within a second when the companion has something else to say.
 * Over another app's music or video only when the user lets it pause them ([mayPlay], [play]).
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

    /**
     * Whether a song may start (F-26): no call is on, and no other app is playing music or video
     * unless [pauseOthers] (the user lets the song pause them). The app's own sounds don't count.
     */
    fun mayPlay(pauseOthers: Boolean = false): Boolean =
        audio.mode == AudioManager.MODE_NORMAL && (pauseOthers || !othersPlaying())

    /** Another app playing music or video, by [OtherAudio]; not the app's own background sound or voice. */
    private fun othersPlaying(): Boolean = audio.activePlaybackConfigurations.any {
        OtherAudio.isOthers(it.audioAttributes.usage, it.audioAttributes.contentType)
    }

    /**
     * Plays [file] to its end, or until [stop]. False when it couldn't start (no focus, or the file
     * can't be played). Takes the audio focus for its length: others duck under it, or with
     * [pauseOthers] they pause and go on once it ends (F-26).
     */
    suspend fun play(file: File, pauseOthers: Boolean = false): Boolean {
        val gain = if (pauseOthers) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val focus = AudioFocusRequest.Builder(gain).setAudioAttributes(attributes).build()
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

/**
 * Which sounds playing on the device are another app's music or video (F-26). The app's own sounds
 * are marked so they aren't: the background sound as sonification (kit-voice `AmbientPlayer`), the
 * voice and the materials' audio as navigation guidance. A song itself is never playing when asked.
 */
object OtherAudio {
    fun isOthers(usage: Int, contentType: Int): Boolean =
        (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME) &&
            contentType != AudioAttributes.CONTENT_TYPE_SONIFICATION
}
