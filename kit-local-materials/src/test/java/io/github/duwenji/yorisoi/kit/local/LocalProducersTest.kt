package io.github.duwenji.yorisoi.kit.local

import io.github.duwenji.yorisoi.core.format.material.CueParams
import io.github.duwenji.yorisoi.core.format.material.MaterialType
import io.github.duwenji.yorisoi.core.format.material.Prelude
import io.github.duwenji.yorisoi.core.format.material.ProducerPolicy
import io.github.duwenji.yorisoi.core.format.material.StudioCategory
import io.github.duwenji.yorisoi.kit.channel.material.MaterialContext
import io.github.duwenji.yorisoi.kit.channel.material.MaterialRequest
import io.github.duwenji.yorisoi.kit.channel.material.MaterialResult
import io.github.duwenji.yorisoi.kit.channel.material.Subject
import io.github.duwenji.yorisoi.kit.channel.ResourceChannels
import io.github.duwenji.yorisoi.kit.geo.Geohash
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

/** builtin:studio and builtin:note as channels' cues ask them (API-MATERIALS「組み込みの素材提供者」, P-8). */
class LocalProducersTest {
    @get:Rule val tmp = TemporaryFolder()

    private val lat = 35.7275
    private val lon = 139.7671
    private val now = Instant.parse("2026-10-31T03:00:00Z")

    private class Fixed(val list: CellList) : LocalApi {
        override suspend fun cells(cells: List<String>, notes: Boolean, studios: Boolean) = list
        override suspend fun reactions(heard: List<Ref>, opened: List<Ref>, liked: List<String>) = Unit
    }

    private fun studio(id: String, category: String, at: Pair<Double, Double> = lat to lon) =
        Studio(id, "谷中銀座商店会", category, "お知らせ$id", null, listOf(Geohash.encode(at.first, at.second, 6)), now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)), null)

    private fun feed(vararg studios: Studio, notes: List<Note> = emptyList()) = LocalFeed(
        Fixed(CellList(notes, studios.toList(), now, 300)), LocalMemory(File(tmp.root, "m.json")), null,
        { LocalSettings(listenNotes = true, listenStudios = true, explained = true, termsVersion = 1) }, { now },
    ).also { runBlocking { it.onLocation(lat, lon) } }

    private object TestChannels : ResourceChannels("test-channels", "standard", listOf("standard"))

    private val station = TestChannels.load().first()

    private fun ask(producer: LocalProducer, params: CueParams = CueParams.NONE, subject: Subject = Subject.None) = runBlocking {
        producer.produce(MaterialRequest(subject, MaterialType.TEXT, MaterialContext(station), params = params))
    }

    private fun text(r: MaterialResult) = ((r as MaterialResult.Ready).material.content as LocalReading).text

    @Test
    fun `they are built in, fetched by area, and people's own words`() {
        listOf(StudioProducer { null }, NoteProducer { null }).forEach {
            assertNull(ProducerPolicy.violation(it.descriptor))
            assertTrue(it.descriptor.verbatim)
        }
    }

    @Test
    fun `a studio comes by the cue's categories, with the prelude before where it comes from`() {
        val f = feed(studio("FOOD", "food"), studio("TEMPLE", "shrine-temple"))
        val producer = StudioProducer { f }
        val r = ask(producer, CueParams(categories = listOf(StudioCategory.SHRINE_TEMPLE), prelude = Prelude.BY_THE_WAY))
        assertEquals("ところで、谷中銀座商店会からのお知らせです。お知らせTEMPLE", text(r))
        assertEquals("谷中銀座商店会", (r as MaterialResult.Ready).material.credit?.author)
        // Nothing of those categories is left; a cue naming none takes what there is.
        assertEquals(MaterialResult.None, ask(producer, CueParams(categories = listOf(StudioCategory.SHRINE_TEMPLE))))
        assertEquals("谷中銀座商店会からのお知らせです。お知らせFOOD", text(ask(producer)))
    }

    @Test
    fun `a spot cue takes what is in the spot's cell, not the phone's`() {
        val away = 35.75 to 139.80
        val f = feed(studio("HERE", "event"), studio("AWAY", "event", at = away))
        val spot = object : Subject.Spot {
            override val id = "way/1"; override val name = "上野"; override val lat = away.first; override val lon = away.second
            override val category = "名所"; override val distanceM: Int? = 30
        }
        assertEquals("谷中銀座商店会からのお知らせです。お知らせAWAY", text(ask(StudioProducer { f }, subject = spot)))
    }

    @Test
    fun `a note comes once, and nothing comes outside a walk or ride`() {
        val f = feed(notes = listOf(Note("N1", "坂の上から富士山", Geohash.encode(lat, lon, 7), null, now.plus(Duration.ofHours(1)), false)))
        assertEquals("ちょっとひと息。この辺りを通った人の一言です。坂の上から富士山", text(ask(NoteProducer { f }, CueParams(prelude = Prelude.PAUSE))))
        assertEquals(MaterialResult.None, ask(NoteProducer { f }))
        assertEquals(MaterialResult.Reason.NOT_PREPARED, (ask(NoteProducer { null }) as MaterialResult.Unavailable).reason)
    }
}
