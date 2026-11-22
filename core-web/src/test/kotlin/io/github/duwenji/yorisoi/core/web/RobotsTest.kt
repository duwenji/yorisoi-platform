package io.github.duwenji.yorisoi.core.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotsTest {
    private val text = """
        # sample
        User-agent: *
        Disallow: /private/
        Disallow: /*.pdf$
        Allow: /private/open/

        User-agent: yorisoi
        Disallow: /cgi-bin/
    """.trimIndent()

    @Test
    fun `the group for the product token is used when there is one`() {
        val r = Robots.parse(text, "yorisoi")
        assertFalse(r.allows("https://a.example/cgi-bin/x"))
        assertTrue(r.allows("https://a.example/private/x"))
    }

    @Test
    fun `the star group, longest match and closing dollar`() {
        val r = Robots.parse(text, "otherbot")
        assertFalse(r.allows("https://a.example/private/x"))
        assertTrue(r.allows("https://a.example/private/open/x"))
        assertFalse(r.allows("https://a.example/doc/a.pdf"))
        assertTrue(r.allows("https://a.example/doc/a.pdf?x=1"))
        assertTrue(r.allows("https://a.example/news/"))
    }

    @Test
    fun `an empty Disallow and no robots allow everything`() {
        assertTrue(Robots.parse("User-agent: *\nDisallow:\n", "yorisoi").allows("https://a.example/x"))
        assertTrue(Robots.ALLOW_ALL.allows("https://a.example/x"))
    }
}
