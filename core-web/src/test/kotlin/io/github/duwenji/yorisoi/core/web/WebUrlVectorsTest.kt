package io.github.duwenji.yorisoi.core.web

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every sample in docs/contracts/api/web-url-vectors.json (AGENT A-17・A-24・A-26) holds. */
class WebUrlVectorsTest {
    private val doc = JSONObject(File(System.getProperty("webUrlVectors")).readText(Charsets.UTF_8))
    private val exclusion = ExclusionList.parse(doc.getJSONObject("exclusion").toString())

    @Test
    fun `the samples hold`() {
        val cases = doc.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val url = c.getString("url")
            val from = c.optString("from").takeUnless { c.isNull("from") || it.isEmpty() }
            val fromLedger = !c.optBoolean("inChannel", false)
            val policy = ReadPolicy(if (c.optBoolean("exclusionNeverFetched", false)) null else exclusion)
            val deny = policy.check(url, source = from ?: url, fromLedger = fromLedger, depth = c.getInt("depth"), robotsAllows = !c.optBoolean("robotsDisallow", false))
            val expected = if (c.getBoolean("allow")) null else c.getString("reason")
            assertEquals("case $i: $url", expected, deny?.id)
        }
        assertTrue(cases.length() >= 10)
    }
}
