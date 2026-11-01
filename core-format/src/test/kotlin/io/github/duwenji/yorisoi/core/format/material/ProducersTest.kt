package io.github.duwenji.yorisoi.core.format.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** API-MATERIALS「組み込みの制作元」and 方針 P-1. */
class ProducersTest {
    @Test
    fun builtInProducersAreAllowed() {
        BuiltInProducers.all.forEach { assertNull(it.id, ProducerPolicy.violation(it)) }
    }

    @Test
    fun builtInIdsAreUniqueAndPrefixed() {
        val ids = BuiltInProducers.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith(BuiltInProducers.PREFIX) })
        ids.forEach { assertEquals(it, BuiltInProducers.byId(it)?.id) }
        assertNull(BuiltInProducers.byId("builtin:unknown"))
    }

    @Test
    fun reviewedProducerMayOnlyPrefetchFixedMaterialWithoutExposingTheSubject() {
        val collection = ProducerDescriptor(
            "collection:kamakura-audio-guide", 2, setOf(MaterialType.AUDIO), setOf(SubjectKind.SPOT),
            Trust.REVIEWED, Fetch.PREFETCH, Exposure.NONE, generated = false,
        )
        assertNull(ProducerPolicy.violation(collection))
        assertNotNull(ProducerPolicy.violation(collection.copy(fetch = Fetch.ON_DEMAND)))
        assertNotNull(ProducerPolicy.violation(collection.copy(exposure = Exposure.SUBJECT)))
        assertNotNull(ProducerPolicy.violation(collection.copy(generated = true)))
        assertNotNull(ProducerPolicy.violation(collection.copy(trust = Trust.TRIAL, fetch = Fetch.ON_DEMAND)))
    }

    @Test
    fun areaFetchingAndPeoplesOwnWordsAreForBuiltInProducersOnly() {
        // P-7: fetching by area, and telling the cell, are left to the app's own code.
        val collection = ProducerDescriptor(
            "collection:x", 1, setOf(MaterialType.TEXT), setOf(SubjectKind.SPOT), Trust.REVIEWED, Fetch.PREFETCH, Exposure.NONE, generated = false,
        )
        assertNotNull(ProducerPolicy.violation(collection.copy(fetch = Fetch.AREA)))
        assertNotNull(ProducerPolicy.violation(collection.copy(exposure = Exposure.AREA)))
        // P-8: people's own words are never generated.
        assertNotNull(ProducerPolicy.violation(BuiltInProducers.STUDIO.copy(generated = true)))
        listOf(BuiltInProducers.STUDIO, BuiltInProducers.NOTE).forEach {
            assertTrue(it.id, it.verbatim)
            assertEquals(it.id, Fetch.AREA, it.fetch)
            assertEquals(it.id, Exposure.AREA, it.exposure)
        }
        assertEquals(setOf("builtin:studio", "builtin:note"), BuiltInProducers.withShorthand.map { it.id }.toSet())
    }
}
