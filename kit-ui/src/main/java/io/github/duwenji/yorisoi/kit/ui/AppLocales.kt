package io.github.duwenji.yorisoi.kit.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import io.github.duwenji.yorisoi.core.text.AppLanguage
import java.util.Locale

/**
 * The app's language on Android (REQ-LANGUAGES D-3・D-4・F-1〜F-3): which one the device asks for, and the
 * resources, dates and voice in it. Apps wrap their activities' and services' contexts with [wrap], so
 * `stringResource` and `getString` give the app's language whatever the device's is.
 *
 * Resources are kept as `values` (Japanese), `values-en`, `values-b+zh+Hans` and `values-ko`.
 */
object AppLocales {
    /** The device's languages, in the user's order, as BCP 47 tags; not changed by [wrap]. */
    fun deviceTags(): List<String> {
        val list = Resources.getSystem().configuration.locales
        return (0 until list.size()).map { list[it].toLanguageTag() }
    }

    /** The language from the device's (F-1), for the setting "follow the device" (F-3). */
    fun fromDevice(): AppLanguage = AppLanguage.resolve(deviceTags())

    /** The locale for [language]'s resources and formats: Simplified Chinese with its script, so `values-b+zh+Hans` is found. */
    fun locale(language: AppLanguage): Locale = when (language) {
        AppLanguage.JA -> Locale.JAPAN
        AppLanguage.EN -> Locale.US
        AppLanguage.ZH_HANS -> Locale.forLanguageTag("zh-Hans-CN")
        AppLanguage.KO -> Locale.KOREA
    }

    /** The locale for the voice (F-7): the engines know Simplified Chinese as `zh-CN`. */
    fun voiceLocale(language: AppLanguage): Locale = when (language) {
        AppLanguage.ZH_HANS -> Locale.CHINA
        else -> locale(language)
    }

    /** [base] with its resources in [language] (F-4). Use it in `attachBaseContext` of activities and services. */
    fun wrap(base: Context, language: AppLanguage): Context {
        val locale = locale(language)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
