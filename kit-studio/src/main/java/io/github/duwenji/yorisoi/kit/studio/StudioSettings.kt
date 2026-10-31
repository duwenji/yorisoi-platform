package io.github.duwenji.yorisoi.kit.studio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user chose about the studio (REQ-STUDIO D-12, D-24): whether notes and placements are
 * read out, whether the first explanation was shown, and the version of the note terms agreed to.
 * Nothing is read out before the explanation.
 */
data class StudioSettings(
    val listenNotes: Boolean = false,
    val listenPlacements: Boolean = false,
    val explained: Boolean = false,
    /** 0 until the note terms are agreed to. */
    val termsVersion: Int = 0,
)

/** [StudioSettings] in the app's preferences, as a flow for the screens. */
class StudioSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("studio", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<StudioSettings> = _settings.asStateFlow()

    fun update(change: (StudioSettings) -> StudioSettings) {
        val next = change(_settings.value)
        prefs.edit()
            .putBoolean(KEY_NOTES, next.listenNotes)
            .putBoolean(KEY_PLACEMENTS, next.listenPlacements)
            .putBoolean(KEY_EXPLAINED, next.explained)
            .putInt(KEY_TERMS, next.termsVersion)
            .apply()
        _settings.value = next
    }

    private fun read() = StudioSettings(
        listenNotes = prefs.getBoolean(KEY_NOTES, false),
        listenPlacements = prefs.getBoolean(KEY_PLACEMENTS, false),
        explained = prefs.getBoolean(KEY_EXPLAINED, false),
        termsVersion = prefs.getInt(KEY_TERMS, 0),
    )

    private companion object {
        const val KEY_NOTES = "listen_notes"
        const val KEY_PLACEMENTS = "listen_placements"
        const val KEY_EXPLAINED = "explained"
        const val KEY_TERMS = "terms_version"
    }
}
