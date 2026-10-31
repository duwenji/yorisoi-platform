package io.github.duwenji.yorisoi.kit.studio

import io.github.duwenji.yorisoi.kit.geo.Geohash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** What to read out next: a note, or a placement with its audio when it has some. */
sealed interface Reading {
    /** What the companion says, the source first (REQ-STUDIO D-10). */
    val spoken: String

    data class OfNote(val note: Note) : Reading {
        override val spoken: String
            get() = (note.spotName?.let { "${it}のそばで、" }.orEmpty()) + "この辺りを通った人の一言です。" + note.text
    }

    data class OfPlacement(val placement: Placement, val audio: File?) : Reading {
        override val spoken: String get() = "${placement.providerName}からのお知らせです。" + placement.text
    }
}

/** Where a placement's audio is kept on the device once fetched (API-STUDIO D-22). */
fun interface ClipFetcher {
    /** The local file of [audio], fetched and checked against its SHA-256; null when it can't be. */
    suspend fun fetch(audio: PlacementAudio): File?
}

/**
 * The studio for one walk or ride (kit-studio README「散歩・走行の間の流れ」): fetches the cells
 * around the user, keeps what is near, and hands out one thing at a time within the hourly limits.
 * Reactions wait in [memory] until [finish].
 */
class StudioFeed(
    private val api: StudioApi,
    private val memory: StudioMemory,
    private val clips: ClipFetcher? = null,
    private val settings: () -> StudioSettings,
    private val now: () -> Instant = Instant::now,
) {
    private val lock = Mutex()
    private var cellsKey: String? = null
    private var fetchedAt: Instant? = null
    private var refetchAfter: Duration = Duration.ofSeconds(DEFAULT_REFETCH_SEC)
    private var notes: List<Note> = emptyList()
    private var placements: List<Placement> = emptyList()
    private val audio = mutableMapOf<String, File>()
    private val readNotes = ArrayDeque<Instant>()
    private val readPlacements = ArrayDeque<Instant>()
    private var lat = Double.NaN
    private var lon = Double.NaN

    private val _heard = MutableStateFlow<List<Reading>>(emptyList())
    /** What was read out on this walk or ride, newest last, for the screen. */
    val heard: StateFlow<List<Reading>> = _heard.asStateFlow()

    /** Sends what an earlier walk couldn't (API-STUDIO ST-5). */
    suspend fun start() = flush()

    /** Sends this walk's reactions; what can't be sent waits for the next start. */
    suspend fun finish() = flush()

    private suspend fun flush() {
        while (true) {
            val pending = memory.pending()
            if (pending.isEmpty) return
            try {
                api.reactions(pending.heard, pending.opened, pending.liked)
            } catch (e: StudioException) {
                return
            }
            memory.sent(pending, now())
        }
    }

    /**
     * A new position: refetches when the cells around changed, or the studio's wait has passed.
     * Failures leave the last list; the walk goes on without the studio (API-STUDIO エラー表).
     */
    suspend fun onLocation(lat: Double, lon: Double) = lock.withLock {
        this.lat = lat
        this.lon = lon
        val s = settings()
        if (!s.listenNotes && !s.listenPlacements) {
            notes = emptyList()
            placements = emptyList()
            return@withLock
        }
        val cells = around(lat, lon, 6)
        val key = cells.sorted().joinToString(",") + "|${s.listenNotes}|${s.listenPlacements}"
        val t = now()
        val due = fetchedAt?.let { Duration.between(it, t) >= refetchAfter } ?: true
        if (key == cellsKey && !due) return@withLock
        try {
            val list = api.cells(cells, s.listenNotes, s.listenPlacements)
            notes = list.notes
            placements = list.placements
            refetchAfter = Duration.ofSeconds(list.refetchAfterSec.toLong().coerceAtLeast(60))
            for (p in list.placements) {
                val a = p.audio ?: continue
                if (p.id !in audio) clips?.fetch(a)?.let { audio[p.id] = it }
            }
        } catch (e: StudioException) {
            // too_many_requests and the like: tried again at the next cell or wait.
            if (e.code == "studio_paused") {
                notes = emptyList()
                placements = emptyList()
            }
        }
        cellsKey = key
        fetchedAt = t
    }

    /**
     * The next thing to read out near the user, or null (kit-studio README「選ぶ」): a placement
     * first, then a note, within the last hour's limits. It counts as read once returned.
     */
    suspend fun next(): Reading? = lock.withLock {
        if (lat.isNaN()) return@withLock null
        val t = now()
        val s = settings()
        val hourAgo = t.minus(Duration.ofHours(1))
        while (readNotes.firstOrNull()?.isBefore(hourAgo) == true) readNotes.removeFirst()
        while (readPlacements.firstOrNull()?.isBefore(hourAgo) == true) readPlacements.removeFirst()

        val here6 = Geohash.encode(lat, lon, 6)
        val day = dayOf(t)
        if (s.listenPlacements && readPlacements.size < PLACEMENTS_PER_HOUR) {
            val placement = placements
                .filter { here6 in it.cells && !it.startsAt.isAfter(t) && it.endsAt.isAfter(t) && withinHours(it.hours, t) }
                .filter { !memory.placementHeardOn(it.id, day) }
                .sortedWith(compareBy<Placement> { it.cells.size }.thenBy { it.endsAt })
                .firstOrNull()
            if (placement != null) {
                memory.markPlacementHeard(placement.id, day, t)
                readPlacements.addLast(t)
                return@withLock Reading.OfPlacement(placement, audio[placement.id]).also(::record)
            }
        }
        if (s.listenNotes && readNotes.size < NOTES_PER_HOUR) {
            val near7 = around(lat, lon, 7).toSet()
            val note = notes.firstOrNull { it.cell in near7 && !it.mine && it.expiresAt.isAfter(t) && !memory.noteHeard(it.id) }
            if (note != null) {
                memory.markNoteHeard(note.id, t)
                readNotes.addLast(t)
                return@withLock Reading.OfNote(note).also(::record)
            }
        }
        null
    }

    /** A note or placement opened on the screen (API-STUDIO `opened`). */
    fun opened(reading: Reading) = memory.markOpened(refOf(reading), now())

    fun like(note: Note) = memory.like(note.id, now())

    /** Leaves out the notes of a blocked author until the next fetch brings the list without them. */
    suspend fun dropNote(noteId: String) = lock.withLock { notes = notes.filter { it.id != noteId } }

    private fun record(reading: Reading) {
        _heard.value = _heard.value + reading
    }

    companion object {
        const val NOTES_PER_HOUR = 10
        const val PLACEMENTS_PER_HOUR = 2
        private const val DEFAULT_REFETCH_SEC = 300L
        private val JAPAN: ZoneId = ZoneId.of("Asia/Tokyo")

        /** The day in Japan, `yyyy-MM-dd`: a placement is read once a day. */
        fun dayOf(t: Instant): String = ZonedDateTime.ofInstant(t, JAPAN).toLocalDate().toString()

        fun refOf(reading: Reading): Ref = when (reading) {
            is Reading.OfNote -> Ref.note(reading.note.id)
            is Reading.OfPlacement -> Ref.placement(reading.placement.id)
        }

        /** Whether [t] is within a placement's reading hours in Japan (none: any time). */
        fun withinHours(hours: Hours?, t: Instant): Boolean {
            if (hours == null) return true
            val time = ZonedDateTime.ofInstant(t, JAPAN).toLocalTime().toString().take(5)
            return time >= hours.from && time < hours.to
        }

        /** The cell of [precision] the point is in, and the eight around it. */
        fun around(lat: Double, lon: Double, precision: Int): List<String> {
            val bits = precision * 5
            val dLon = 360.0 / (1L shl ((bits + 1) / 2))
            val dLat = 180.0 / (1L shl (bits / 2))
            return (-1..1).flatMap { y -> (-1..1).map { x -> Geohash.encode((lat + y * dLat).coerceIn(-89.999999, 89.999999), wrap(lon + x * dLon), precision) } }.distinct()
        }

        private fun wrap(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0
    }
}
