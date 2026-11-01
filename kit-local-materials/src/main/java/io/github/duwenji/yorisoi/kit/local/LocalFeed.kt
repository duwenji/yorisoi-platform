package io.github.duwenji.yorisoi.kit.local

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

/** What to read out next: a note, or a studio with its audio when it has some. */
sealed interface Reading {
    /** What the companion says, the source first (REQ-STUDIO D-10). */
    val spoken: String

    data class OfNote(val note: Note) : Reading {
        override val spoken: String
            get() = (note.spotName?.let { "${it}のそばで、" }.orEmpty()) + "この辺りを通った人の一言です。" + note.text
    }

    data class OfStudio(val studio: Studio, val audio: File?) : Reading {
        override val spoken: String get() = "${studio.providerName}からのお知らせです。" + studio.text
    }
}

/** Where a studio's audio is kept on the device once fetched (API-LOCAL-MATERIALS D-22). */
fun interface ClipFetcher {
    /** The local file of [audio], fetched and checked against its SHA-256; null when it can't be. */
    suspend fun fetch(audio: StudioAudio): File?
}

/**
 * The studio for one walk or ride (kit-studio README「散歩・走行の間の流れ」): fetches the cells
 * around the user, keeps what is near, and hands out one thing at a time within the hourly limits.
 * Reactions wait in [memory] until [finish].
 */
class LocalFeed(
    private val api: LocalApi,
    private val memory: LocalMemory,
    private val clips: ClipFetcher? = null,
    private val settings: () -> LocalSettings,
    private val now: () -> Instant = Instant::now,
) {
    private val lock = Mutex()
    private var cellsKey: String? = null
    private var fetchedAt: Instant? = null
    private var refetchAfter: Duration = Duration.ofSeconds(DEFAULT_REFETCH_SEC)
    private var notes: List<Note> = emptyList()
    private var studios: List<Studio> = emptyList()
    private val audio = mutableMapOf<String, File>()
    private val readNotes = ArrayDeque<Instant>()
    private val readStudios = ArrayDeque<Instant>()
    private var lat = Double.NaN
    private var lon = Double.NaN

    private val _heard = MutableStateFlow<List<Reading>>(emptyList())
    /** What was read out on this walk or ride, newest last, for the screen. */
    val heard: StateFlow<List<Reading>> = _heard.asStateFlow()

    /** Sends what an earlier walk couldn't (API-LOCAL-MATERIALS ST-5). */
    suspend fun start() = flush()

    /** Sends this walk's reactions; what can't be sent waits for the next start. */
    suspend fun finish() = flush()

    private suspend fun flush() {
        while (true) {
            val pending = memory.pending()
            if (pending.isEmpty) return
            try {
                api.reactions(pending.heard, pending.opened, pending.liked)
            } catch (e: LocalException) {
                return
            }
            memory.sent(pending, now())
        }
    }

    /**
     * A new position: refetches when the cells around changed, or the studio's wait has passed.
     * Failures leave the last list; the walk goes on without the studio (API-LOCAL-MATERIALS エラー表).
     */
    suspend fun onLocation(lat: Double, lon: Double) = lock.withLock {
        this.lat = lat
        this.lon = lon
        val s = settings()
        if (!s.listenNotes && !s.listenStudios) {
            notes = emptyList()
            studios = emptyList()
            return@withLock
        }
        val cells = around(lat, lon, 6)
        val key = cells.sorted().joinToString(",") + "|${s.listenNotes}|${s.listenStudios}"
        val t = now()
        val due = fetchedAt?.let { Duration.between(it, t) >= refetchAfter } ?: true
        if (key == cellsKey && !due) return@withLock
        try {
            val list = api.cells(cells, s.listenNotes, s.listenStudios)
            notes = list.notes
            studios = list.studios
            refetchAfter = Duration.ofSeconds(list.refetchAfterSec.toLong().coerceAtLeast(60))
            for (p in list.studios) {
                val a = p.audio ?: continue
                if (p.id !in audio) clips?.fetch(a)?.let { audio[p.id] = it }
            }
        } catch (e: LocalException) {
            // too_many_requests and the like: tried again at the next cell or wait.
            if (e.code == "local_paused") {
                notes = emptyList()
                studios = emptyList()
            }
        }
        cellsKey = key
        fetchedAt = t
    }

    /** Where to choose around: a cue's spot or point; null for where the phone is (an idle cue). */
    data class Near(val lat: Double, val lon: Double)

    /**
     * The next studio in the cell of [near] (or of the phone), of [categories] when given, or null
     * (kit-local-materials README「選ぶ」): within its period and hours, once a day, at most
     * [STUDIOS_PER_HOUR] in the last hour. It counts as read once returned.
     */
    suspend fun nextStudio(categories: Set<String>? = null, near: Near? = null): Reading.OfStudio? = lock.withLock {
        val (lat, lon) = near?.let { it.lat to it.lon } ?: (this.lat to this.lon)
        if (lat.isNaN() || !settings().listenStudios) return@withLock null
        val t = now()
        trim(readStudios, t)
        if (readStudios.size >= STUDIOS_PER_HOUR) return@withLock null
        val here6 = Geohash.encode(lat, lon, 6)
        val day = dayOf(t)
        val studio = studios
            .filter { here6 in it.cells && !it.startsAt.isAfter(t) && it.endsAt.isAfter(t) && withinHours(it.hours, t) }
            .filter { categories == null || it.category in categories }
            .filter { !memory.studioHeardOn(it.id, day) }
            .sortedWith(compareBy<Studio> { it.cells.size }.thenBy { it.endsAt })
            .firstOrNull() ?: return@withLock null
        memory.markStudioHeard(studio.id, day, t)
        readStudios.addLast(t)
        Reading.OfStudio(studio, audio[studio.id]).also(::record)
    }

    /**
     * The next note in the cells around [near] (or the phone), or null: not one of mine, not read
     * before, at most [NOTES_PER_HOUR] in the last hour. It counts as read once returned.
     */
    suspend fun nextNote(near: Near? = null): Reading.OfNote? = lock.withLock {
        val (lat, lon) = near?.let { it.lat to it.lon } ?: (this.lat to this.lon)
        if (lat.isNaN() || !settings().listenNotes) return@withLock null
        val t = now()
        trim(readNotes, t)
        if (readNotes.size >= NOTES_PER_HOUR) return@withLock null
        val near7 = around(lat, lon, 7).toSet()
        val note = notes.firstOrNull { it.cell in near7 && !it.mine && it.expiresAt.isAfter(t) && !memory.noteHeard(it.id) }
            ?: return@withLock null
        memory.markNoteHeard(note.id, t)
        readNotes.addLast(t)
        Reading.OfNote(note).also(::record)
    }

    private fun trim(read: ArrayDeque<Instant>, t: Instant) {
        val hourAgo = t.minus(Duration.ofHours(1))
        while (read.firstOrNull()?.isBefore(hourAgo) == true) read.removeFirst()
    }

    /** A note or studio opened on the screen (API-LOCAL-MATERIALS `opened`). */
    fun opened(reading: Reading) = memory.markOpened(refOf(reading), now())

    fun like(note: Note) = memory.like(note.id, now())

    /** Leaves out the notes of a blocked author until the next fetch brings the list without them. */
    suspend fun dropNote(noteId: String) = lock.withLock { notes = notes.filter { it.id != noteId } }

    private fun record(reading: Reading) {
        _heard.value = _heard.value + reading
    }

    companion object {
        const val NOTES_PER_HOUR = 10
        const val STUDIOS_PER_HOUR = 2
        private const val DEFAULT_REFETCH_SEC = 300L
        private val JAPAN: ZoneId = ZoneId.of("Asia/Tokyo")

        /** The day in Japan, `yyyy-MM-dd`: a studio is read once a day. */
        fun dayOf(t: Instant): String = ZonedDateTime.ofInstant(t, JAPAN).toLocalDate().toString()

        fun refOf(reading: Reading): Ref = when (reading) {
            is Reading.OfNote -> Ref.note(reading.note.id)
            is Reading.OfStudio -> Ref.studio(reading.studio.id)
        }

        /** Whether [t] is within a studio's reading hours in Japan (none: any time). */
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
