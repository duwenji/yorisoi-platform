package io.github.duwenji.yorisoi.kit.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.duwenji.yorisoi.core.text.AppLanguage
import io.github.duwenji.yorisoi.core.text.Languages
import io.github.duwenji.yorisoi.core.text.localized

/**
 * The language chosen in the settings (REQ-LANGUAGES F-2・F-3): one of the four, or none to follow the device's
 * (the default). Kept apart from the other settings, so an activity's `attachBaseContext` can read it before
 * anything else is up.
 */
class AppLanguageSetting(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The language chosen, or null to follow the device's (F-3). Kept when the device's changes. */
    var chosen: AppLanguage?
        get() = AppLanguage.of(prefs.getString(KEY, null))
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value.tag) }.commit()
            apply()
        }

    /** The app's language now: the one chosen, else the device's (F-1). */
    val language: AppLanguage get() = chosen ?: AppLocales.fromDevice()

    /** Makes [language] the one the platform's parts use ([Languages.current]), and returns it. */
    fun apply(): AppLanguage = language.also { Languages.current = it }

    companion object {
        private const val PREFS = "app_language"
        private const val KEY = "language"

        /** [base] in the app's language, for `attachBaseContext` of activities and services (F-4). */
        fun wrap(base: Context): Context = AppLocales.wrap(base, AppLanguageSetting(base).apply())
    }
}

/**
 * The setting's choices (F-2・F-3): following the device, then the four in their own names. The app saves the
 * choice with [AppLanguageSetting.chosen] and recreates its screens.
 */
@Composable
fun LanguageChoice(chosen: AppLanguage?, onChoose: (AppLanguage?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.selectableGroup()) {
        val device = AppLocales.fromDevice()
        LanguageRow(
            localized("端末の言語に合わせる", "Follow the device", "跟随设备语言", "기기 언어에 맞추기") + "（${device.selfName}）",
            chosen == null,
        ) { onChoose(null) }
        AppLanguage.entries.forEach { language ->
            LanguageRow(language.selfName, chosen == language) { onChoose(language) }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
