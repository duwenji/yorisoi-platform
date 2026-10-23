package io.github.duwenji.yorisoi.kit.geo

import io.github.duwenji.yorisoi.core.format.SpotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Overpass answers kept on the device (kit-geo README「スポット検索のキャッシュ」). */
class SpotCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000_000_000L
    private val cache by lazy { SpotCache(folder.newFolder("spots"), now = { clock }) }

    @Test
    fun `an answer from nearby is found, one from further away is not`() {
        cache.put(35.0, 139.0, emptySet(), "near", replaceWithinM = 150.0)
        // About 220m north, then about 450m.
        assertEquals("near", cache.find(35.002, 139.0, emptySet(), 300.0, SpotCache.FRESH_MS)?.body())
        assertNull(cache.find(35.004, 139.0, emptySet(), 300.0, SpotCache.FRESH_MS))
    }

    @Test
    fun `the shops asked for must be the same`() {
        cache.put(35.0, 139.0, setOf(SpotKind.FOOD_SHOP, SpotKind.CAFE), "shops", replaceWithinM = 150.0)
        assertNull(cache.find(35.0, 139.0, emptySet(), 300.0, SpotCache.FRESH_MS))
        assertNull(cache.find(35.0, 139.0, setOf(SpotKind.CAFE), 300.0, SpotCache.FRESH_MS))
        assertEquals("shops", cache.find(35.0, 139.0, setOf(SpotKind.CAFE, SpotKind.FOOD_SHOP), 300.0, SpotCache.FRESH_MS)?.body())
    }

    @Test
    fun `an old answer is found only when old ones will do`() {
        cache.put(35.0, 139.0, emptySet(), "old", replaceWithinM = 150.0)
        clock += SpotCache.FRESH_MS + 1
        assertNull(cache.find(35.0, 139.0, emptySet(), 300.0, SpotCache.FRESH_MS))
        assertEquals("old", cache.find(35.0, 139.0, emptySet(), 300.0, SpotCache.MAX_AGE_MS)?.body())
    }

    @Test
    fun `the nearest answer is found`() {
        cache.put(35.0, 139.0, emptySet(), "a", replaceWithinM = 150.0)
        cache.put(35.003, 139.0, emptySet(), "b", replaceWithinM = 150.0)
        assertEquals("b", cache.find(35.002, 139.0, emptySet(), 600.0, SpotCache.FRESH_MS)?.body())
    }

    @Test
    fun `a new answer replaces the overlapping old one`() {
        cache.put(35.0, 139.0, emptySet(), "old", replaceWithinM = 150.0)
        clock += 1000
        cache.put(35.0005, 139.0, emptySet(), "new", replaceWithinM = 150.0)
        assertEquals("new", cache.find(35.0, 139.0, emptySet(), 300.0, SpotCache.FRESH_MS)?.body())
        assertEquals(1, folder.root.resolve("spots").listFiles()!!.size)
    }

    @Test
    fun `answers past the longest age are dropped, and the oldest when over the size`() {
        val small = SpotCache(folder.newFolder("small"), now = { clock }, maxBytes = 10)
        small.put(35.0, 139.0, emptySet(), "12345", replaceWithinM = 150.0)
        clock += 1000
        small.put(36.0, 139.0, emptySet(), "67890", replaceWithinM = 150.0)
        clock += 1000
        small.put(37.0, 139.0, emptySet(), "abcde", replaceWithinM = 150.0)
        assertNull(small.find(35.0, 139.0, emptySet(), 300.0, SpotCache.MAX_AGE_MS))
        assertNotNull(small.find(36.0, 139.0, emptySet(), 300.0, SpotCache.MAX_AGE_MS))

        clock += SpotCache.MAX_AGE_MS + 1
        small.put(38.0, 139.0, emptySet(), "x", replaceWithinM = 150.0)
        assertNull(small.find(37.0, 139.0, emptySet(), 300.0, Long.MAX_VALUE / 2))
    }

    @Test
    fun `clear forgets everything`() {
        cache.put(35.0, 139.0, emptySet(), "a", replaceWithinM = 150.0)
        cache.clear()
        assertNull(cache.find(35.0, 139.0, emptySet(), 300.0, SpotCache.MAX_AGE_MS))
    }
}
