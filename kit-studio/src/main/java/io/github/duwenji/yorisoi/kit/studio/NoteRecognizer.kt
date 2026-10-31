package io.github.duwenji.yorisoi.kit.studio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * A note spoken and written out on the device (REQ-STUDIO D-3, F-1, F-2): the voice never leaves
 * it. Needs Android 12's on-device recognition; without it the app doesn't offer to place notes.
 */
class NoteRecognizer(private val context: Context) {
    /** Whether this device can write out speech on the device itself. */
    val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * Listens once and returns what was said, in one line, or null when nothing was understood.
     * Needs the RECORD_AUDIO permission. Runs on the main thread, as SpeechRecognizer asks.
     */
    suspend fun listen(): String? = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !available) return@withContext null
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine { cont ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (cont.isActive) cont.resume(text?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() })
                    }

                    override fun onError(error: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
                )
                cont.invokeOnCancellation { recognizer.cancel() }
            }
        } finally {
            recognizer.destroy()
        }
    }

    companion object {
        /** A note is up to 60 characters (API-STUDIO); longer speech is cut there. */
        const val MAX_CHARS = 60

        fun fit(text: String): String = text.codePoints().limit(MAX_CHARS.toLong()).toArray().let { String(it, 0, it.size) }
    }
}
