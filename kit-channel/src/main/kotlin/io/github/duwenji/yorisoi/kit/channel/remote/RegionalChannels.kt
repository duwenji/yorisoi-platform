package io.github.duwenji.yorisoi.kit.channel.remote

import io.github.duwenji.yorisoi.core.format.ListedChannel
import io.github.duwenji.yorisoi.core.format.material.Regions

/**
 * A channel of a list the user added whose region the device is in (REQ-REGIONAL-CHANNELS F-1).
 *
 * @param prefix the longest of the channel's `regions` the place's geohash starts with
 * @param installed whether it can be switched to without taking it in first
 */
data class RegionalChannel(val providerId: String, val channel: ListedChannel, val prefix: String, val installed: Boolean) {
    /** Whether the region is narrow enough to tell the user about (D-6): five letters or more, about 5km across. */
    val notifies: Boolean get() = prefix.length >= RegionalChannels.NOTICE_MIN_PREFIX
}

/**
 * Finds the regional channels for a place (REQ-REGIONAL-CHANNELS D-1・F-1・F-2): on the device, from the lists
 * already taken in, so the place isn't sent anywhere (N-1). The lists are already narrowed to this app's
 * activities ([ChannelSync.providers], D-7).
 */
object RegionalChannels {
    /** The shortest prefix the user is told about (D-6). Shorter regions only lead the choice. */
    const val NOTICE_MIN_PREFIX = 5

    /** The longest prefix a list may give (the management system takes 1–8 letters). */
    private const val PRECISION = 8

    /** Listed statuses a channel can be used from: taken in already, or to be taken in now. */
    private val USABLE = setOf(ListedStatus.AVAILABLE, ListedStatus.INSTALLED, ListedStatus.UPDATE_PENDING)

    /** The regional channels at [lat], [lon], the narrowest region first, then by name (D-8). */
    fun near(lat: Double, lon: Double, providers: List<ProviderView>): List<RegionalChannel> {
        val here = Regions.geohash(lat, lon, PRECISION)
        return providers.filter { it.record.enabled && it.listValid }.flatMap { p ->
            p.channels.filter { it.status in USABLE }.mapNotNull { v ->
                v.channel.regions.filter { it.isNotEmpty() && here.startsWith(it) }.maxByOrNull { it.length }
                    ?.let { RegionalChannel(p.record.id, v.channel, it, v.installed != null) }
            }
        }.sortedWith(compareByDescending<RegionalChannel> { it.prefix.length }.thenBy { it.channel.name })
    }
}

/** Where the days the user was told of each channel are kept (REQ-REGIONAL-CHANNELS F-10). */
interface RegionalNoticeStore {
    /** The day ([java.time.LocalDate] as text) the user was last told of [key], or null. */
    fun lastNoticed(key: String): String?
    fun noticed(key: String, day: String)
}

/** What to tell when entering a region (REQ-REGIONAL-CHANNELS F-8・F-9): one channel, and how many others match. */
data class RegionalNotice(val channel: RegionalChannel, val others: Int) {
    /** The fixed line, shown on a card and read out (D-9): no AI, and nothing the publisher wrote but the name. */
    val text: String get() =
        "この地域には「${channel.channel.name}」のチャンネルがあります。" +
            (if (others > 0) "ほかに${others}つあります。" else "") + "チャンネルの選択から使えます。"
}

/**
 * Decides when to tell the user of a regional channel (REQ-REGIONAL-CHANNELS D-3・D-6・D-8・F-8・F-10): once a day
 * a channel, only for narrow regions, never for the channel in use.
 */
class RegionalNotices(private val store: RegionalNoticeStore) {
    /**
     * What to tell now, or null: the narrowest region's channel not told of today and not in use, among [near].
     *
     * @param inUse the station in use, as `providerId/channelId` (a version after `@` is ignored), or null for a built-in one
     * @param today the device's date, as [java.time.LocalDate] text
     */
    fun due(near: List<RegionalChannel>, inUse: String?, today: String): RegionalNotice? {
        val using = inUse?.substringBefore('@')
        val candidates = near.filter { it.notifies && key(it) != using }
        val first = candidates.firstOrNull { store.lastNoticed(key(it)) != today } ?: return null
        return RegionalNotice(first, candidates.size - 1)
    }

    /** Remembers that [notice] was given on [today], so its channel isn't told again that day (F-10). */
    fun told(notice: RegionalNotice, today: String) = store.noticed(key(notice.channel), today)

    companion object {
        /** A regional channel as `providerId/channelId`, as a station in use is named. */
        fun key(c: RegionalChannel): String = "${c.providerId}/${c.channel.id}"
    }
}
