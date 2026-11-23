package io.github.duwenji.yorisoi.kit.agent.tools

import io.github.duwenji.yorisoi.core.agent.HintMaker
import io.github.duwenji.yorisoi.core.agent.HintPage
import io.github.duwenji.yorisoi.core.agent.Origin
import io.github.duwenji.yorisoi.core.agent.ResultBook
import io.github.duwenji.yorisoi.core.agent.ResultRole
import io.github.duwenji.yorisoi.core.agent.ToolOutcome
import io.github.duwenji.yorisoi.core.agent.ToolRunner
import io.github.duwenji.yorisoi.core.web.PageCurrent
import io.github.duwenji.yorisoi.core.web.ReadPolicy
import io.github.duwenji.yorisoi.core.web.Source
import io.github.duwenji.yorisoi.kit.calendar.KoyomiFact
import io.github.duwenji.yorisoi.kit.web.Fetched
import io.github.duwenji.yorisoi.kit.web.HintCache
import io.github.duwenji.yorisoi.kit.web.PageFetcher
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The preparer's standard tools (platform ADR-AGENT A-6・A-7・A-12・A-69), for one preparation.
 *
 * [prefetch] runs the tools taken before the preparation (calendar, weather, season_obs, local_events) for the
 * scenes the channel lets it use, and [sourceList] gives the sources it may read; both go in the input (A-4).
 * [run] answers the tools the preparer asks for: `read_page` (reached only within [policy]'s ReadPolicy and
 * turned into hints by [hintModel]; the preparer never sees a page's text, A-18), `nearby_spots` and `materials`.
 *
 * @param hintModel the hint maker's call (kit-llm `AiGateway.generate(Purpose.HINTS, …)`)
 */
class StandardTools(
    private val input: PrepInput,
    private val policy: AgentPolicy,
    private val reads: ReadPolicy,
    private val fetcher: PageFetcher,
    private val hints: HintCache,
    private val hintModel: suspend (system: String, user: String) -> String,
    private val place: String,
    private val zone: ZoneId = ZoneId.of("Asia/Tokyo"),
) : ToolRunner {
    /** A page the preparer may name: a source's own URL or a link found on a page, with how it was reached. */
    private data class Reachable(val url: String, val source: Source, val sourceUrl: String, val depth: Int, val title: String, val date: LocalDate?)

    private val byNumber = HashMap<String, Reachable>()
    private var links = 0
    private val spotNumbers = HashMap<String, String>()

    /** The sources as the input lists them: 「P1 福井市 お知らせ（市区町村）」 (A-4), at most 20. */
    fun sourceList(): String {
        if (!policy.web) return "なし"
        val lines = ArrayList<String>()
        input.sources.take(20).forEachIndexed { i, s ->
            val read = s.reads.first()
            val id = "P${i + 1}"
            byNumber[id] = Reachable(read.url, s, read.url, 0, read.title ?: s.name, null)
            lines += "$id ${s.name}${read.title?.let { " $it" }.orEmpty()}（${KIND_LABEL[s.kind.id] ?: s.kind.id}）"
        }
        return lines.joinToString("\n").ifEmpty { "なし" }
    }

    /** The tools taken before the preparation, as numbered facts in [book], rendered for the input (A-12). */
    fun prefetch(book: ResultBook): String = buildString {
        fun section(title: String, results: List<io.github.duwenji.yorisoi.core.agent.Result>) {
            append("## ").append(title).append('\n').append(book.render(results)).append('\n')
        }
        if ("calendar" in policy.scenes || "season" in policy.scenes) {
            val facts = input.koyomi
                .filter { it.kind !in policy.calendarSkip }
                .sortedBy { if (it.kind in policy.calendarPrefer) 0 else 1 }
                .take(8)
                .map { koyomiText(it) }
            section("暦", book.add("calendar", facts, Origin("暦", "national")))
        }
        if ("season" in policy.scenes) {
            input.weather?.let { w ->
                val sunset = w.sunsetAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }?.let { "、日の入り %d時%02d分".format(it.hour, it.minute) }.orEmpty()
                section("天気", book.add("weather", listOf("今は${w.description}、気温 ${w.temperatureC.toInt()} 度$sunset"), Origin("天気予報", "national")))
            }
            val obs = input.seasonObs?.around(input.prefecture, input.date).orEmpty()
            section("季節の観測", book.add("season_obs", obs.map { it.fact(input.date) }, Origin("気象庁", "national", url = obs.firstOrNull()?.source)))
        }
        if ("events" in policy.scenes) {
            val ev = input.events.take(10).map { n ->
                val e = n.event
                "${dayText(e.startDate)}${if (e.endDate != e.startDate) "〜${e.endDate.monthValue}月${e.endDate.dayOfMonth}日" else ""} ${e.venue}で${e.name}"
            }
            section("催し", book.add("local_events", ev, Origin(input.events.firstOrNull()?.event?.source?.publisher ?: "自治体", "municipality")))
        }
    }

    override suspend fun run(name: String, input: JSONObject, book: ResultBook): ToolOutcome = when (name) {
        "read_page" -> readPages(input.getJSONArray("ids").let { a -> (0 until a.length()).map(a::getString) }, book)
        "nearby_spots" -> nearby(input.optBoolean("ahead", false), book)
        "materials" -> materials(input.optString("spot").ifEmpty { null }, input.optString("topic").ifEmpty { null }, book)
        else -> ToolOutcome("この道具はありません", emptyList())
    }

    private suspend fun readPages(ids: List<String>, book: ResultBook): ToolOutcome {
        if (!policy.web) return ToolOutcome("ページは読めません", emptyList())
        val out = StringBuilder()
        val added = ArrayList<io.github.duwenji.yorisoi.core.agent.Result>()
        val pages = ArrayList<Pair<Reachable, io.github.duwenji.yorisoi.core.web.Page>>()
        for (id in ids.take(HintMaker.MAX_PAGES)) {
            val r = byNumber[id] ?: run { out.append("$id: 番号がありません\n"); null } ?: continue
            val deny = reads.check(r.url, r.sourceUrl, r.source.fromLedger, r.depth)
            if (deny != null) {
                out.append("$id: 読めません（${deny.id}）\n")
                continue
            }
            run {
                when (val f = fetcher.fetch(r.url, list = r.depth == 0)) {
                    is Fetched.AsFeed -> {
                        val items = f.items.filter { reads.check(it.url, r.sourceUrl, r.source.fromLedger, r.depth + 1) == null }.take(10)
                        val lines = items.map { item ->
                            val lid = "L${++links}"
                            byNumber[lid] = Reachable(item.url, r.source, r.sourceUrl, r.depth + 1, item.title, item.date)
                            "$lid ${item.date?.let { "${it.monthValue}月${it.dayOfMonth}日 " }.orEmpty()}${item.title}"
                        }
                        added += book.add("read_page", lines, role = ResultRole.LINKS)
                        out.append("$id（一覧）:\n").append(lines.joinToString("\n").ifEmpty { "（なし）" }).append('\n')
                    }
                    is Fetched.AsPage -> {
                        // A page reached from a feed item takes the item's date when it states none itself.
                        pages += r to (if (f.page.published == null && r.date != null) f.page.copy(published = r.date) else f.page)
                        val lines = f.page.links.filter { it.title.isNotEmpty() && reads.check(it.url, r.sourceUrl, r.source.fromLedger, r.depth + 1) == null }.take(10).map { l ->
                            val lid = "L${++links}"
                            byNumber[lid] = Reachable(l.url, r.source, r.sourceUrl, r.depth + 1, l.title, null)
                            "$lid ${l.title}"
                        }
                        if (lines.isNotEmpty()) {
                            added += book.add("read_page", lines, role = ResultRole.LINKS)
                            out.append("$id のリンク:\n").append(lines.joinToString("\n")).append('\n')
                        }
                    }
                    is Fetched.NotRead -> out.append("$id: 読めませんでした（${f.reason}）\n")
                }
            }
        }
        // Hints for the pages read, in one call to the hint maker (A-18); pages with no date or older than 30 days
        // serve only a spot's preparation.
        // A page older than that still serves when its text names a period that includes today or a day soon (an
        // event's page written before it opened and never updated, A-18).
        val usable = pages.filter { (_, p) ->
            input.forSpot || (p.published != null && ChronoUnit.DAYS.between(p.published, input.date) <= 30) || PageCurrent.of(p.text, input.date)
        }
        pages.filter { it !in usable }.forEach { (r, _) -> out.append("${r.title}: 日付がないか古いので使いません\n") }
        if (usable.isNotEmpty()) {
            val fresh = ArrayList<Pair<Reachable, io.github.duwenji.yorisoi.core.web.Page>>()
            val made = HashMap<Reachable, List<String>>()
            for ((r, p) in usable) hints.get(r.url, p.text, r.depth == 0)?.let { made[r] = it } ?: fresh.add(r to p)
            if (fresh.isNotEmpty()) {
                val hp = fresh.map { (_, p) -> HintPage(p.title, p.text) }
                val answer = runCatching { hintModel(HintMaker.system, HintMaker.request(hp, place, input.date.toString())) }.getOrNull()
                if (answer != null) HintMaker.read(answer, hp).forEachIndexed { i, h -> made[fresh[i].first] = h; hints.put(fresh[i].first.url, fresh[i].second.text, h) }
            }
            for ((r, p) in usable) {
                val h = made[r].orEmpty()
                val origin = Origin(r.source.name, r.source.kind.id, p.title.ifEmpty { r.title }, r.url, p.published?.toString())
                if (h.isEmpty()) {
                    added += book.add("read_page", listOf("${r.title}: 手がかりなし"), origin, ResultRole.EMPTY_PAGE)
                    out.append("${r.title}: 手がかりなし\n")
                } else {
                    val rs = book.add("read_page", h, origin)
                    added += rs
                    out.append("${r.title}:\n").append(book.render(rs)).append('\n')
                }
            }
        }
        return ToolOutcome(out.toString().trim().ifEmpty { "（なし）" }, added)
    }

    private fun nearby(ahead: Boolean, book: ResultBook): ToolOutcome {
        val spots = input.spots
            .filter { it.poi.category !in policy.spotsSkip }
            .filter { !ahead || it.side == "ahead" }
            .sortedBy { if (it.poi.category in policy.spotsPrefer) 0 else 1 }
            .take(20)
        val lines = spots.map { s ->
            val sid = spotNumbers.getOrPut(s.poi.id) { "S${spotNumbers.size + 1}" }
            "$sid ${s.poi.name}（${s.poi.category}）、${s.compass}に約 ${(s.distanceM + 50) / 100 * 100} m"
        }
        val rs = book.add("nearby_spots", lines, Origin("地図データ", "national"))
        return ToolOutcome(book.render(rs), rs)
    }

    private fun materials(spot: String?, topic: String?, book: ResultBook): ToolOutcome {
        val spotId = spot?.let { s -> spotNumbers.entries.firstOrNull { it.value == s }?.key }
        val picked = input.materials
            .filter { policy.references || !it.reference }
            .filter { m -> (spotId != null && m.spotId == spotId) || (topic != null && m.topic?.contains(topic) == true) }
            .take(5)
        val rs = picked.flatMap { m -> book.add("materials", listOf(m.text), Origin(m.author ?: "素材", "channel")) }
        return ToolOutcome(book.render(rs), rs)
    }

    /**
     * A calendar fact in one line of at most [FACT_MAX] characters: its note whole, or cut at a 「、」「。」 that leaves it
     * whole up to there, or left out; never cut mid-word (the speaker read 「感謝しあ」 out as it was).
     */
    private fun koyomiText(f: KoyomiFact): String {
        val head = "${dayText(f.date)}${if (f.date == input.date) "は" else "、"}${f.name}"
        val room = FACT_MAX - head.length - 2
        val note = f.note.trim().trimEnd('。')
        val fit = when {
            note.isEmpty() || room <= 0 -> null
            note.length <= room -> note
            else -> note.take(room).let { cut -> cut.lastIndexOfAny(charArrayOf('、', '。')).takeIf { it > 0 }?.let { cut.take(it) } }
        }
        return if (fit != null) "$head（$fit）" else head
    }

    private fun dayText(d: LocalDate): String {
        val days = ChronoUnit.DAYS.between(input.date, d)
        val md = "${d.monthValue}月${d.dayOfMonth}日"
        return when (days) {
            0L -> "今日 $md"
            1L -> "明日 $md"
            -1L -> "昨日 $md"
            else -> md
        }
    }

    companion object {
        /** One result is one fact of at most this many characters (ADR-AGENT A-12). */
        const val FACT_MAX = 60
        private val KIND_LABEL = mapOf(
            "national" to "国の機関", "prefecture" to "都道府県", "municipality" to "市区町村",
            "tourism" to "観光協会", "association" to "団体", "channel" to "チャンネル",
        )
    }
}
