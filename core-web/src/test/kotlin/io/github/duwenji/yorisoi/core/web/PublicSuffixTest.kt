package io.github.duwenji.yorisoi.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSuffixTest {
    @Test
    fun `a prefecture and its city are different sites under lg_jp`() {
        assertEquals("city.fukui.lg.jp", PublicSuffix.registrableDomain("www.city.fukui.lg.jp"))
        assertEquals("pref.fukui.lg.jp", PublicSuffix.registrableDomain("www.pref.fukui.lg.jp"))
        assertNotEquals(PublicSuffix.registrableDomain("www.city.fukui.lg.jp"), PublicSuffix.registrableDomain("www.pref.fukui.lg.jp"))
        assertEquals(PublicSuffix.registrableDomain("kanko.city.fukui.lg.jp"), PublicSuffix.registrableDomain("www.city.fukui.lg.jp"))
    }

    @Test
    fun `the geographic domains of the older style work`() {
        assertEquals("city.yawatahama.ehime.jp", PublicSuffix.registrableDomain("www.city.yawatahama.ehime.jp"))
        assertEquals("city.sapporo.jp", PublicSuffix.registrableDomain("www.city.sapporo.jp"))
        assertEquals("iejima.org", PublicSuffix.registrableDomain("www.iejima.org"))
    }

    @Test
    fun `a suffix itself has no registrable domain`() {
        assertNull(PublicSuffix.registrableDomain("lg.jp"))
        assertNull(PublicSuffix.registrableDomain("co.jp"))
    }

    @Test
    fun `shared services are told apart`() {
        assertTrue(PublicSuffix.isSharedService("kankou-a.hatenablog.com"))
        assertEquals("kankou-a.hatenablog.com", PublicSuffix.registrableDomain("kankou-a.hatenablog.com"))
        assertFalse(PublicSuffix.isSharedService("www.city.fukui.lg.jp"))
        assertFalse(PublicSuffix.isSharedService("www.example.com"))
    }
}
