package io.github.duwenji.yorisoi.core.text

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every sample in docs/contracts/api/text-safety-vectors.json (AGENT A-52) holds. */
class TextSafetyVectorsTest {
    private val cases = JSONObject(File(System.getProperty("textSafetyVectors")).readText(Charsets.UTF_8)).getJSONArray("cases")

    private fun contactCode(text: String): String? = when (ContactCheck.violation(text)) {
        null -> null
        "a URL" -> "url"
        "an email address" -> "email"
        "a phone number" -> "phone"
        else -> "other"
    }

    private fun writtenCode(text: String): String? = when {
        WrittenText.findUrl(text) != null -> "url"
        WrittenText.hasHeading(text) -> "heading"
        else -> null
    }

    @Test
    fun `the samples hold`() {
        var checked = 0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val text = c.getString("text")
            if (c.has("contact")) {
                assertEquals("contact: $text", c.opt("contact").takeUnless { it == JSONObject.NULL }, contactCode(text)); checked++
            }
            if (c.has("written")) {
                assertEquals("written: $text", c.opt("written").takeUnless { it == JSONObject.NULL }, writtenCode(text)); checked++
            }
            if (c.has("instruction")) {
                assertEquals("instruction: $text", c.getBoolean("instruction"), InstructionCheck.looksLikeInstruction(text)); checked++
            }
            if (c.has("spoken")) {
                assertEquals("spoken: $text", c.getString("spoken"), SpokenText.spoken(text)); checked++
            }
        }
        assertTrue("no sample was checked", checked > 20)
    }
}
