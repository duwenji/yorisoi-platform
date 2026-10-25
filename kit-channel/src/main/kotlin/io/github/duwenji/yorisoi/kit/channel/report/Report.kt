package io.github.duwenji.yorisoi.kit.channel.report

import io.github.duwenji.yorisoi.kit.channel.Station
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Who hears of a problem with a channel or with something the AI said (platform ADR-004 H-11): the
 * operator of the list the channel came from (its discovery document's `contact`), or the app's
 * developer for the built-in channels and lists without one.
 */
data class ReportTarget(val channelName: String, val channelKey: String, val listName: String?, val contact: String)

/**
 * A report is an email the user writes and sends themselves; the app sends nothing on its own. The
 * app opens the user's mail app with [subject] and [body] to [ReportTarget.contact].
 *
 * @param appName the app as the report names it, as `SanpoGuide`
 * @param appContact the app's contact, as in its privacy policy
 */
class Report(private val appName: String, private val appContact: String) {
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"))

    fun target(station: Station): ReportTarget =
        ReportTarget(station.name, station.key, station.source?.providerName, station.source?.contact ?: appContact)

    /**
     * The draft's body: the channel and, for a line, when it was said and what (ADR-004 未決事項 No.5).
     * Never where: the user adds it only if they want to.
     */
    fun body(target: ReportTarget, said: Pair<Long, String>?, appVersion: String): String = buildString {
        appendLine("（何が問題だったかを書いてください）")
        appendLine()
        appendLine("---")
        appendLine("チャンネル: ${target.channelName}（${target.channelKey}）")
        target.listName?.let { appendLine("リスト: $it") }
        said?.let { (at, text) ->
            appendLine("発言の時刻: ${time.format(Instant.ofEpochMilli(at))}")
            appendLine("発言: $text")
        }
        appendLine("アプリ: $appName $appVersion")
        append("位置は含めていません。")
    }

    fun subject(target: ReportTarget, said: Pair<Long, String>?): String =
        if (said != null) "AI の発言について（${target.channelName}）" else "チャンネルについて（${target.channelName}）"
}
