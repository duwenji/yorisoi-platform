package io.github.duwenji.yorisoi.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-LANGUAGES D-1・D-3・D-5・D-6・F-1. */
class AppLanguageTest {
    @Test
    fun `the first of the device's languages that is one of the four, else English`() {
        assertEquals(AppLanguage.KO, AppLanguage.resolve(listOf("fr-FR", "ko-KR", "ja-JP")))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(listOf("fr-FR", "de-DE")))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(emptyList()))
        assertEquals(AppLanguage.JA, AppLanguage.resolve(listOf("ja_JP")))
    }

    @Test
    fun `any Chinese is Simplified Chinese`() {
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.resolve(listOf("zh-Hant-TW")))
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.of("zh-CN"))
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.of("zh-Hans"))
        assertNull(AppLanguage.of("fr"))
        assertNull(AppLanguage.of(null))
    }

    @Test
    fun `each language is found from its own tag`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.of(it.tag)) }
    }

    @Test
    fun `lengths in Japanese characters become lengths that take as long to read`() {
        assertEquals(150, AppLanguage.JA.fromJapanese(150))
        assertEquals(420, AppLanguage.EN.fromJapanese(150))
        assertEquals(120, AppLanguage.ZH_HANS.fromJapanese(150))
        assertEquals(75, AppLanguage.JA.chars(15))
    }

    @Test
    fun `only the other languages add an instruction, which names the language`() {
        assertNull(AppLanguage.JA.instruction)
        assertTrue(AppLanguage.EN.instruction!!.contains("英語"))
        assertTrue(AppLanguage.ZH_HANS.instruction!!.contains("中国語（簡体字）"))
        assertTrue(AppLanguage.KO.instruction!!.contains("韓国語"))
    }
}
