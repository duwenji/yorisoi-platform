# 季節の歌の直し（REQ-KOYOMI X-1）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 組み込みのチャンネルで季節の歌が流れるようにする（REQ-KOYOMI F-40〜F-44）。

**Architecture:** 両アプリ（yorisoi-sanpoguide・yorisoi-cycleguide）だけを変える。platform（kit-songs・kit-channel）は変えないので、platform の版上げは要らない。①最初の 1 曲目を話題の順番より先にする、②AI がなくても歌は定型の前置きで流す、③端末に曲がないときは起動後すぐ（Wi-Fi になりしだい）取る、④設定に端末の曲の数を出す、⑤SanpoGuide で前の形で保存した出来事の変更を正しく読む。

**Tech Stack:** Kotlin、Android（WorkManager・Jetpack Compose）、JUnit 4、platform の kit-songs（`SongLibrary`・`SongSeason`）

**Spec:** `yorisoi-platform/docs/requirements/REQ-KOYOMI.md`（F-40〜F-44、AC-9〜AC-12）

## Global Constraints

- 曲を取るのは、従量制でない回線で電池が少なくないとき、散歩・走行の最中でないときだけ（REQ-NEARBY-TALK F-33・F-34、API-SONGS AS-1）
- 1 曲目は歩き始めて（走り始めて）15 分を過ぎてから（F-42）。1 回の散歩・走行で 2 曲まで、間は 20 分以上（REQ-NEARBY-TALK F-23。変えない）
- 歌は、AI が使えないときも定型の前置きで流す（F-43）。AI が使えないときに、ほかの雑談は出さない（REQ-NEARBY-TALK D-9。変えない）
- CycleGuide は、利用者が「雑談をする」を切っているときは歌も流さない（REQ-NEARBY-TALK F-27。変えない）
- 設定の文言: 曲があるとき「今の季節の曲: N 曲」、ないとき「まだ取っていません（Wi-Fi のときに取ります）」（F-41）
- コミットのメッセージは日本語、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 各アプリで `fix/songs-play` ブランチを main から切って作業する

## Review Focus

1. 散歩の途中で AI の 1 時間の上限に達した（AI は設定済み）→ それでも歌は定型の前置きで流れるべき（Task 2・6 のテスト）
2. 歌を選んだのに、流す直前にほかのアプリが音楽を始めて流せなかった → 次の雑談でまた歌を選び続けて黙り込まないこと（Task 1・5 のテスト: 直前 2 回に SONG があれば先にしない）
3. 歩き始めたが、まだ 1 曲も端末にない → 歌を先にせず、いつもの話題の順番になること（Task 1・5 のテスト）
4. 新しい形で保存した変更で、利用者が自分で雑談を切った → 読み直しても切ったままであること（Task 4 のテスト）
5. 前の形で保存した変更が、静かなチャンネル（`chat`・`song` が偽）にある → 読み直しても偽のままであること（Task 4 のテスト）

---

## yorisoi-sanpoguide

テストの実行: `cd yorisoi-sanpoguide && ./gradlew :app:testDebugUnitTest --tests "<クラス名>"`（platform を GitHub Packages から取れないときは `-Pyorisoi.platformDir=../yorisoi-platform` を付ける）

### Task 1: 1 曲目を話題の順番より先にする（SanpoGuide、F-42）

**Files:**
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/SongMoment.kt`（`NOT_BEFORE_MS`）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/ChatTopic.kt`（`next`）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/companion/TalkDeciderTest.kt`

**Interfaces:**
- Produces: `ChatTopic.next(recent: List<ChatTopic>, elapsedMs: Long, nearbyKnown: Boolean, songReady: Boolean = false, songFirst: Boolean = false): ChatTopic?`、`SongMoment.NOT_BEFORE_MS = 15 * 60_000L`

- [ ] **Step 1: ブランチを切る**

```bash
cd yorisoi-sanpoguide && git checkout main && git pull && git checkout -b fix/songs-play
```

- [ ] **Step 2: 失敗するテストを書く**

`TalkDeciderTest.kt` の「a season's song」の節に足し、既存の `not in the first ten minutes...` を 15 分に直す。

```kotlin
    @Test
    fun `the walk's first song doesn't wait its turn (REQ-KOYOMI F-42)`() {
        assertEquals(ChatTopic.SONG, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER), 0, nearbyKnown = true, songReady = true, songFirst = true))
        // No song on the device yet: the usual turn.
        assertEquals(ChatTopic.TIME_OF_DAY, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER), 0, nearbyKnown = true, songReady = false, songFirst = true))
        // A song was just tried and couldn't be played: not again at once.
        assertEquals(ChatTopic.TIME_OF_DAY, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER, ChatTopic.SONG), 0, nearbyKnown = true, songReady = true, songFirst = true))
        // After the first, songs take their turn.
        assertEquals(ChatTopic.TIME_OF_DAY, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER), 0, nearbyKnown = true, songReady = true, songFirst = false))
    }

    @Test
    fun `not in the first fifteen minutes, at most two a walk, twenty minutes apart (F-23, REQ-KOYOMI F-42)`() {
        assertEquals(false, SongMoment.allowed(14 * minute, 0, 0, now))
        assertEquals(true, SongMoment.allowed(15 * minute, 0, 0, now))
        assertEquals(false, SongMoment.allowed(40 * minute, 1, now - 19 * minute, now))
        assertEquals(true, SongMoment.allowed(40 * minute, 1, now - 20 * minute, now))
        assertEquals(false, SongMoment.allowed(90 * minute, 2, now - 60 * minute, now))
    }
```

既存の `not in the first ten minutes, at most two a walk, twenty minutes apart (F-23, R-5)` は消す（上に置き換える。元のテストの残りの assert があれば、新しいテストの末尾に移す）。

- [ ] **Step 3: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*TalkDeciderTest"`
Expected: コンパイルエラー（`songFirst` という引数がない）

- [ ] **Step 4: 直す**

`SongMoment.kt`:

```kotlin
    /** Not in the first minutes of a walk: music out of nowhere would startle (R-5); the first song comes then (REQ-KOYOMI F-42). */
    const val NOT_BEFORE_MS = 15 * 60_000L
```

`ChatTopic.kt` の `next` を次にする（KDoc に `songFirst` を足す）:

```kotlin
        /**
         * The next topic after [recent] (oldest first), in the order above: not one of the last
         * [NOT_AGAIN], nor one there is nothing for ([nearbyKnown]: spots around; the body before
         * [BODY_AFTER_MS]; a song unless [songReady]). Null when none is left. The walk's first song
         * ([songFirst]) doesn't wait its turn (platform REQ-KOYOMI F-42).
         */
        fun next(recent: List<ChatTopic>, elapsedMs: Long, nearbyKnown: Boolean, songReady: Boolean = false, songFirst: Boolean = false): ChatTopic? {
            if (songFirst && songReady && SONG !in recent.takeLast(NOT_AGAIN)) return SONG
            val usable = entries.filter {
                (it != NEARBY || nearbyKnown) && (it != BODY || elapsedMs >= BODY_AFTER_MS) && (it != SONG || songReady) &&
                    it !in recent.takeLast(NOT_AGAIN)
            }
            val after = recent.lastOrNull()?.ordinal ?: -1
            return usable.firstOrNull { it.ordinal > after } ?: usable.firstOrNull()
        }
```

- [ ] **Step 5: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*TalkDeciderTest"`
Expected: PASS

- [ ] **Step 6: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/SongMoment.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/ChatTopic.kt app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/companion/TalkDeciderTest.kt
git commit -m "fix: 散歩の 1 曲目の季節の歌を、話題の順番を待たずに流す（REQ-KOYOMI F-42）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: AI がなくても歌を流す（SanpoGuide、F-42・F-43）

**Files:**
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/TalkDecider.kt`（`TalkSituation` に `songFirst`、`Rule("chat")`）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/walk/WalkService.kt`（状況を作るところ・`playSong`）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/companion/TalkDeciderTest.kt`

**Interfaces:**
- Consumes: `ChatTopic.next(..., songReady, songFirst)`（Task 1）
- Produces: `TalkSituation.songFirst: Boolean = false`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
    @Test
    fun `without the AI, no chat but the season's song, after its fixed word (REQ-KOYOMI F-43)`() {
        // aiReady is false both with no AI set up and with the hour's limit reached (WalkService).
        assertEquals(TalkDecision.Chat(ChatTopic.SONG), decide(quietStretch.copy(aiReady = false, songReady = true)))
        // No song to play: nothing at all.
        assertEquals(TalkDecision.Nothing, decide(quietStretch.copy(aiReady = false, songReady = false)))
        // Not again right after a song that couldn't be played.
        assertEquals(TalkDecision.Nothing, decide(quietStretch.copy(aiReady = false, songReady = true, chatTopics = listOf(ChatTopic.SONG))))
        // Still not on a quiet level or a channel without chat.
        assertTrue(decide(quietStretch.copy(aiReady = false, songReady = true, level = TalkLevel.QUIET)) !is TalkDecision.Chat)
        assertEquals(TalkDecision.Nothing, decide(quietStretch.copy(aiReady = false, songReady = true, events = allEvents - TalkEventKind.CHAT)))
    }

    @Test
    fun `the walk's first song comes before the other topics (REQ-KOYOMI F-42)`() {
        assertEquals(TalkDecision.Chat(ChatTopic.SONG), decide(quietStretch.copy(songReady = true, songFirst = true)))
        assertEquals(TalkDecision.Chat(ChatTopic.SEASON_WEATHER), decide(quietStretch.copy(songReady = true, songFirst = false)))
    }
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*TalkDeciderTest"`
Expected: コンパイルエラー（`songFirst` がない）

- [ ] **Step 3: `TalkSituation` と規則を直す**

`TalkDecider.kt` の `TalkSituation` で、`songReady` の次に足す:

```kotlin
    /** Whether the walk's first song is still to come: it doesn't wait its turn (platform REQ-KOYOMI F-42). */
    val songFirst: Boolean = false,
```

`Rule("chat", ...)` を次にする:

```kotlin
        // Nothing else to say for the small-talk gap: a word between, with the AI only, while walking
        // (standing still is the rest remark's). Not on a quiet level (platform REQ-NEARBY-TALK F-14〜F-18).
        // Without the AI there is no chat, but a season's song still plays after its fixed word (REQ-KOYOMI F-43).
        Rule("chat", setOf(WalkGate.CHAT_GAP, WalkGate.MOVING, WalkGate.SETTLED)) { s ->
            if (s.level == TalkLevel.QUIET || TalkEventKind.CHAT !in s.events) return@Rule null
            // A chat the AI didn't answer isn't asked again at once.
            if (s.now - s.lastChatAt < s.level.minGapMs) return@Rule null
            if (!s.aiReady) {
                return@Rule ChatTopic.SONG.takeIf { s.songReady && it !in s.chatTopics.takeLast(ChatTopic.NOT_AGAIN) }?.let(TalkDecision::Chat)
            }
            ChatTopic.next(s.chatTopics, s.elapsedMs, s.nearbyKnown, s.songReady, s.songFirst)?.let(TalkDecision::Chat)
        },
```

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*TalkDeciderTest"`
Expected: PASS（既存の `no chat without the AI...` も、`songReady` が偽なので通る）

- [ ] **Step 5: `WalkService` をつなぐ**

状況を作るところ（`songReady = songReady(s, station, now),` の行）の次に足す:

```kotlin
            songFirst = s.songsPlayed == 0,
```

`playSong` の AI の前置きを、AI が使えるときだけ頼むようにする（使えないときに AI を呼びに行かない）:

```kotlin
        val aiReady = app.settings.settings.value.isConfigured && !app.guides.limitReached()
        val intro = if (aiReady) app.narration.aiLine(TalkEvent.Chat(ChatTopic.SONG, song.title), s, weather) else null
```

（元の `val intro = app.narration.aiLine(TalkEvent.Chat(ChatTopic.SONG, song.title), s, weather)` の 1 行を置き換える。続く `if (intro != null) ... else 定型の前置き` はそのまま）

- [ ] **Step 6: 全体のテストとビルド**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/TalkDecider.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/walk/WalkService.kt app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/companion/TalkDeciderTest.kt
git commit -m "fix: AI が使えないときも季節の歌を定型の前置きで流す（REQ-KOYOMI F-43）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: 端末に曲がないときはすぐ取り、設定に曲の数を出す（SanpoGuide、F-40・F-41）

**Files:**
- Create: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/SongShelf.kt`
- Create: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/walk/SongFetchWorker.kt`
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/SanpoApp.kt`（`songs = SongLibrary(...)` の後）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/ui/SettingsViewModel.kt`（`setPlaySongs` の後）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/ui/SettingsScreen.kt`（「季節の歌を流す」の `SwitchRow` の後）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/companion/SongShelfTest.kt`

**Interfaces:**
- Produces: `SongShelf.keptNow(library: SongLibrary, now: Long): Int`、`SongShelf.line(kept: Int): String`、`SongFetchWorker.ifNoneKept(context: Context, kept: Int)`

- [ ] **Step 1: 失敗するテストを書く**

`SongShelfTest.kt`:

```kotlin
package io.github.duwenji.yorisoi.sanpoguide.companion

import io.github.duwenji.yorisoi.kit.songs.SongLibrary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar

class SongShelfTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun song(id: String, season: String, bytes: Int) = """
        {"id":"$id","title":"$id","seasons":["$season"],"timesOfDay":[],"weather":[],"durationSec":30,
         "file":"$id.m4a","bytes":$bytes,"sha256":"${"0".repeat(64)}",
         "rights":{"lyricist":{"name":"a","died":1900},"composer":{"name":"b","died":1900},"score":"s"}}
    """.trimIndent()

    private fun library(vararg kept: Pair<String, Int>): SongLibrary {
        val dir = tmp.newFolder("songs")
        File(dir, "catalog.json").writeText(
            """{"format":"yorisoi-songs","version":1,"updatedAt":"2026-11-10T00:00:00Z","credit":"c","songs":[""" +
                listOf(song("momiji", "autumn", 10), song("hana", "spring", 10), song("furusato", "all", 10), song("akatombo", "autumn", 10)).joinToString(",") +
                "]}",
        )
        File(dir, "audio").mkdirs()
        kept.forEach { (id, size) -> File(dir, "audio/$id.m4a").writeBytes(ByteArray(size)) }
        return SongLibrary(dir, "https://example.invalid/", "test")
    }

    private val autumn = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 12, 0) }.timeInMillis

    @Test
    fun `counts the songs of the season now and of all seasons that are kept whole`() {
        // momiji (autumn) and furusato (all) whole; hana is spring; akatombo is cut short.
        assertEquals(2, SongShelf.keptNow(library("momiji" to 10, "furusato" to 10, "hana" to 10, "akatombo" to 3), autumn))
    }

    @Test
    fun `none before a catalog or a sound was fetched`() {
        assertEquals(0, SongShelf.keptNow(library(), autumn))
        assertEquals(0, SongShelf.keptNow(SongLibrary(tmp.newFolder("empty"), "https://example.invalid/", "test"), autumn))
    }

    @Test
    fun `the line under the switch (REQ-KOYOMI F-41)`() {
        assertEquals("今の季節の曲: 2 曲", SongShelf.line(2))
        assertEquals("まだ取っていません（Wi-Fi のときに取ります）", SongShelf.line(0))
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*SongShelfTest"`
Expected: コンパイルエラー（`SongShelf` がない）

- [ ] **Step 3: `SongShelf` を書く**

```kotlin
package io.github.duwenji.yorisoi.sanpoguide.companion

import io.github.duwenji.yorisoi.kit.songs.SongLibrary
import io.github.duwenji.yorisoi.kit.songs.SongSeason

/** The season's songs on the device now (platform REQ-KOYOMI F-40, F-41). */
object SongShelf {
    /** How many songs of the season at [now] (or of all seasons) are kept whole on the device. */
    fun keptNow(library: SongLibrary, now: Long): Int {
        val season = SongSeason.now(now)
        return library.catalog?.songs.orEmpty().count { (season in it.seasons || "all" in it.seasons) && library.fileOf(it) != null }
    }

    /** The line under the settings' switch. */
    fun line(kept: Int): String = if (kept > 0) "今の季節の曲: $kept 曲" else "まだ取っていません（Wi-Fi のときに取ります）"
}
```

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*SongShelfTest"`
Expected: PASS

- [ ] **Step 5: `SongFetchWorker` を書く**

```kotlin
package io.github.duwenji.yorisoi.sanpoguide.walk

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.duwenji.yorisoi.sanpoguide.SanpoApp

/**
 * The season's songs when the device has none yet (platform REQ-KOYOMI F-40): once, as soon as the
 * network isn't metered, not waiting for the daily fetch ([WayPrefetchWorker]). Never during a walk
 * (API-SONGS AS-1): then it tries again later.
 */
class SongFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SanpoApp
        if (!app.settings.settings.value.playSongs) return Result.success()
        if (WalkService.walking.value) return Result.retry()
        if (!app.songs.refresh(force = app.songs.catalog == null)) return Result.retry()
        Log.i(TAG, "Fetched ${app.songs.prefetch()} songs")
        return Result.success()
    }

    companion object {
        private const val TAG = "SongFetch"
        private const val NAME = "songs-first"

        /** Asks for the songs once when [kept] (the season's songs on the device) is none. */
        fun ifNoneKept(context: Context, kept: Int) {
            if (kept > 0) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<SongFetchWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **Step 6: 起動時に呼ぶ**

`SanpoApp.kt` の `songPlayer = SongPlayer(this)` の次に足す（`companion` パッケージの `SongShelf` と `walk` パッケージの `SongFetchWorker` を import する）:

```kotlin
        if (settings.settings.value.playSongs) SongFetchWorker.ifNoneKept(this, SongShelf.keptNow(songs, System.currentTimeMillis()))
```

- [ ] **Step 7: 設定に曲の数を出す**

`SettingsViewModel.kt` の `setPlaySongs` の次に足す:

```kotlin
    /** The season's songs on the device, for under the switch (platform REQ-KOYOMI F-41). */
    val songsLine: String get() = SongShelf.line(SongShelf.keptNow(getApplication<SanpoApp>().songs, System.currentTimeMillis()))
```

`SettingsScreen.kt` の「季節の歌を流す」の `SwitchRow(...)` の閉じかっこの次に足す:

```kotlin
        if (draft.playSongs) Note(viewModel.songsLine)
```

- [ ] **Step 8: 全体のテストとビルド**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/companion/SongShelf.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/walk/SongFetchWorker.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/SanpoApp.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/ui/SettingsViewModel.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/ui/SettingsScreen.kt app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/companion/SongShelfTest.kt
git commit -m "fix: 端末に季節の歌がないときは Wi-Fi になりしだい取り、設定に曲の数を出す（REQ-KOYOMI F-40・F-41）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: 前の形で保存した出来事の変更を正しく読む（SanpoGuide、F-44）

**Files:**
- Create: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/settings/EventOverrides.kt`
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/settings/SettingsRepository.kt`（コンストラクタ・`save`・`loadStationOverrides`・定数）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/SanpoApp.kt`（`settings = SettingsRepository(this)` と `stations = StationRepository(...)`）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/settings/EventOverridesTest.kt`

**Interfaces:**
- Produces: `EventOverrides.BEFORE_KNOWN: Set<TalkEventKind>`、`EventOverrides.resolve(saved: Set<TalkEventKind>, known: Set<TalkEventKind>, channel: Set<TalkEventKind>): Set<TalkEventKind>`、`SettingsRepository(context: Context, channelEvents: (String) -> Set<TalkEventKind>? = { null })`

- [ ] **Step 1: 失敗するテストを書く**

`EventOverridesTest.kt`:

```kotlin
package io.github.duwenji.yorisoi.sanpoguide.settings

import io.github.duwenji.yorisoi.core.format.TalkEventKind
import io.github.duwenji.yorisoi.core.format.TalkEventKind.CHAT
import io.github.duwenji.yorisoi.core.format.TalkEventKind.FINISH
import io.github.duwenji.yorisoi.core.format.TalkEventKind.MILESTONE
import io.github.duwenji.yorisoi.core.format.TalkEventKind.PREVIEW
import io.github.duwenji.yorisoi.core.format.TalkEventKind.REST
import io.github.duwenji.yorisoi.core.format.TalkEventKind.REVISIT
import io.github.duwenji.yorisoi.core.format.TalkEventKind.SONG
import io.github.duwenji.yorisoi.core.format.TalkEventKind.SPOT
import io.github.duwenji.yorisoi.core.format.TalkEventKind.START
import org.junit.Assert.assertEquals
import org.junit.Test

class EventOverridesTest {
    private val all = TalkEventKind.entries.toSet()
    private val quiet = setOf(START, FINISH)

    @Test
    fun `a change saved before chat and songs existed keeps the channel's own value for them (REQ-KOYOMI F-44)`() {
        // The user turned off revisits on the standard channel, saved as the six known then.
        val saved = setOf(SPOT, MILESTONE, REST, START, FINISH)
        assertEquals(saved + setOf(PREVIEW, CHAT, SONG), EventOverrides.resolve(saved, EventOverrides.BEFORE_KNOWN, all))
    }

    @Test
    fun `on a quiet channel they stay off`() {
        assertEquals(setOf(START, FINISH), EventOverrides.resolve(setOf(START, FINISH), EventOverrides.BEFORE_KNOWN, quiet))
    }

    @Test
    fun `a change saved with every event known is kept as it is`() {
        val saved = all - CHAT - SONG
        assertEquals(saved, EventOverrides.resolve(saved, all, all))
    }

    @Test
    fun `the events known before are the first six`() {
        assertEquals(setOf(SPOT, REVISIT, MILESTONE, REST, START, FINISH), EventOverrides.BEFORE_KNOWN)
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*EventOverridesTest"`
Expected: コンパイルエラー（`EventOverrides` がない）

- [ ] **Step 3: `EventOverrides` を書く**

```kotlin
package io.github.duwenji.yorisoi.sanpoguide.settings

import io.github.duwenji.yorisoi.core.format.TalkEventKind

/**
 * A channel's events as the user changed them, read back so that an event added after the change
 * was saved keeps the channel's own value, instead of being off (platform REQ-KOYOMI F-44).
 */
object EventOverrides {
    /** The events a change knew before the known ones were saved with it: the first six. */
    val BEFORE_KNOWN: Set<TalkEventKind> = setOf(
        TalkEventKind.SPOT, TalkEventKind.REVISIT, TalkEventKind.MILESTONE,
        TalkEventKind.REST, TalkEventKind.START, TalkEventKind.FINISH,
    )

    /** [saved] for the events in [known], and the channel's own value ([channel]) for the rest. */
    fun resolve(saved: Set<TalkEventKind>, known: Set<TalkEventKind>, channel: Set<TalkEventKind>): Set<TalkEventKind> =
        (saved intersect known) + (channel - known)
}
```

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*EventOverridesTest"`
Expected: PASS

- [ ] **Step 5: `SettingsRepository` で使う**

コンストラクタを次にする:

```kotlin
/**
 * @param channelEvents a built-in channel's own events, by its id, to read back a change saved
 *   before an event existed (REQ-KOYOMI F-44); null for a channel the app doesn't have
 */
class SettingsRepository(context: Context, private val channelEvents: (String) -> Set<TalkEventKind>? = { null }) {
```

`save` の `o.events?.let { ... }` の行を次にする:

```kotlin
                o.events?.let {
                    putString(stationPref(id, FIELD_EVENTS), OverrideCodec.encode(it))
                    putString(stationPref(id, FIELD_EVENTS_KNOWN), OverrideCodec.encode(TalkEventKind.entries.toSet()))
                }
```

`loadStationOverrides` の `events = ...` の行を次にする:

```kotlin
                events = OverrideCodec.decodeSet<TalkEventKind>(all[stationPref(id, FIELD_EVENTS)] as? String)?.let { saved ->
                    val known = OverrideCodec.decodeSet<TalkEventKind>(all[stationPref(id, FIELD_EVENTS_KNOWN)] as? String)
                        ?: EventOverrides.BEFORE_KNOWN
                    channelEvents(id)?.let { EventOverrides.resolve(saved, known, it) } ?: saved
                },
```

定数に足す（`FIELD_EVENTS` の次。`FIELDS` にも足す）:

```kotlin
        /** The events this version knew when a change of [FIELD_EVENTS] was saved (REQ-KOYOMI F-44). */
        const val FIELD_EVENTS_KNOWN = "events_known"
        val FIELDS = listOf(FIELD_TALK_LEVEL, FIELD_MOOD_TONE, FIELD_EVENTS, FIELD_EVENTS_KNOWN, FIELD_GUIDE_LENGTH, FIELD_SOUND, FIELD_PREFER)
```

`FIELDS` で id を取り出すとき、`events_known` を `events` より先に当てる必要はない（`_events_known` で終わるキーは `_events` で終わらない）。

- [ ] **Step 6: `SanpoApp` でチャンネルの値を渡す**

`settings = SettingsRepository(this)` の前に組み込みのチャンネルを 1 度だけ読み、両方で使う:

```kotlin
        val builtIn = BuiltInStations.load()
        settings = SettingsRepository(this) { id -> builtIn.firstOrNull { it.id == id }?.manifest?.events }
```

`stations = StationRepository(BuiltInStations.load(), BuiltInStations.STANDARD, choice, appScope, channels.stations)` の `BuiltInStations.load()` を `builtIn` にする。

- [ ] **Step 7: 全体のテストとビルド**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL（`StoredNamesTest` は保存の名前の一覧を確かめている。`events_known` で失敗したら、その一覧に足す）

- [ ] **Step 8: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/settings/EventOverrides.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/settings/SettingsRepository.kt app/src/main/java/io/github/duwenji/yorisoi/sanpoguide/SanpoApp.kt app/src/test/java/io/github/duwenji/yorisoi/sanpoguide/settings/EventOverridesTest.kt
git commit -m "fix: 前の版で保存したチャンネルの出来事の変更で、雑談と季節の歌が止まらないようにする（REQ-KOYOMI F-44）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## yorisoi-cycleguide

テストの実行: `cd yorisoi-cycleguide && ./gradlew :app:testDebugUnitTest --tests "<クラス名>"`（必要なら `-Pyorisoi.platformDir=../yorisoi-platform`）

CycleGuide には組み込みのチャンネルを利用者が変える仕組みがないので、F-44 は当てはまらない。

### Task 5: 1 曲目を話題の順番より先にする（CycleGuide、F-42）

**Files:**
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/SongMoment.kt`（`NOT_BEFORE_MS`）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/ChatTopic.kt`（`next`）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ui/SettingsScreen.kt`（説明の「走り始めて 10 分から」）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/cycleguide/companion/RideDeciderTest.kt`

**Interfaces:**
- Produces: `ChatTopic.next(recent: List<ChatTopic>, elapsedMs: Long, nearbyKnown: Boolean, songReady: Boolean = false, songFirst: Boolean = false): ChatTopic?`、`SongMoment.NOT_BEFORE_MS = 15 * 60_000L`

- [ ] **Step 1: ブランチを切る**

```bash
cd yorisoi-cycleguide && git checkout main && git pull && git checkout -b fix/songs-play
```

- [ ] **Step 2: 失敗するテストを書く**

`RideDeciderTest.kt` に足し、既存の `a song not in the first ten minutes...` を置き換える:

```kotlin
    @Test
    fun `the ride's first song doesn't wait its turn (REQ-KOYOMI F-42)`() {
        assertEquals(ChatTopic.SONG, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER), 0, nearbyKnown = true, songReady = true, songFirst = true))
        assertEquals(ChatTopic.TIME_OF_DAY, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER), 0, nearbyKnown = true, songReady = false, songFirst = true))
        assertEquals(ChatTopic.TIME_OF_DAY, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER, ChatTopic.SONG), 0, nearbyKnown = true, songReady = true, songFirst = true))
        assertEquals(ChatTopic.TIME_OF_DAY, ChatTopic.next(listOf(ChatTopic.SEASON_WEATHER), 0, nearbyKnown = true, songReady = true, songFirst = false))
    }

    @Test
    fun `a song not in the first fifteen minutes, at most two a ride, twenty minutes apart (F-23, REQ-KOYOMI F-42)`() {
        assertEquals(false, SongMoment.allowed(14 * minute, 0, 0, now))
        assertEquals(true, SongMoment.allowed(15 * minute, 0, 0, now))
        assertEquals(false, SongMoment.allowed(40 * minute, 1, now - 19 * minute, now))
        assertEquals(true, SongMoment.allowed(40 * minute, 1, now - 20 * minute, now))
        assertEquals(false, SongMoment.allowed(90 * minute, 2, now - 60 * minute, now))
        assertEquals("秋", SongMoment.seasonWord("autumn"))
    }
```

- [ ] **Step 3: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*RideDeciderTest"`
Expected: コンパイルエラー（`songFirst` がない）

- [ ] **Step 4: 直す**

`SongMoment.kt`:

```kotlin
    /** Not in the first minutes of a ride: music out of nowhere would startle (R-5); the first song comes then (REQ-KOYOMI F-42). */
    const val NOT_BEFORE_MS = 15 * 60_000L
```

`ChatTopic.kt` の `next`:

```kotlin
        /**
         * The next topic after [recent] (oldest first), in the order above: not one of the last
         * [NOT_AGAIN], nor one there is nothing for ([nearbyKnown]: spots around; the body before
         * [BODY_AFTER_MS]; a song unless [songReady]). Null when none is left. The ride's first song
         * ([songFirst]) doesn't wait its turn (platform REQ-KOYOMI F-42).
         */
        fun next(recent: List<ChatTopic>, elapsedMs: Long, nearbyKnown: Boolean, songReady: Boolean = false, songFirst: Boolean = false): ChatTopic? {
            if (songFirst && songReady && SONG !in recent.takeLast(NOT_AGAIN)) return SONG
            val usable = entries.filter {
                (it != NEARBY || nearbyKnown) && (it != BODY || elapsedMs >= BODY_AFTER_MS) && (it != SONG || songReady) &&
                    it !in recent.takeLast(NOT_AGAIN)
            }
            val after = recent.lastOrNull()?.ordinal ?: -1
            return usable.firstOrNull { it.ordinal > after } ?: usable.firstOrNull()
        }
```

`SettingsScreen.kt` の説明の「走り始めて 10 分から」を「走り始めて 15 分から」にする。

- [ ] **Step 5: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*RideDeciderTest"`
Expected: PASS

- [ ] **Step 6: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/SongMoment.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/ChatTopic.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ui/SettingsScreen.kt app/src/test/java/io/github/duwenji/yorisoi/cycleguide/companion/RideDeciderTest.kt
git commit -m "fix: 走行の 1 曲目の季節の歌を、話題の順番を待たずに流す（REQ-KOYOMI F-42）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: AI がなくても歌を流す（CycleGuide、F-42・F-43）

**Files:**
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/RideDecider.kt`（`RideSituation` に `chatOn`・`songFirst`、`Rule("chat")`）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ride/RideService.kt`（状況を作るところ・歌の前置き）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/cycleguide/companion/RideDeciderTest.kt`

**Interfaces:**
- Consumes: `ChatTopic.next(..., songReady, songFirst)`（Task 5）
- Produces: `RideSituation.chatOn: Boolean = chatReady`（利用者の「雑談をする」。AI の有無は問わない）、`RideSituation.songFirst: Boolean = false`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
    @Test
    fun `without the AI, no chat but the season's song, while the rider chats (REQ-KOYOMI F-43)`() {
        val noAi = quietStretch.copy(chatReady = false, chatOn = true)
        assertEquals(RideDecision.Chat(ChatTopic.SONG, 15 * SpeechBudget.CHARS_PER_SEC), decide(noAi.copy(songReady = true)))
        assertEquals(RideDecision.Nothing, decide(noAi.copy(songReady = false)))
        assertEquals(RideDecision.Nothing, decide(noAi.copy(songReady = true, chatTopics = listOf(ChatTopic.SONG))))
        // The rider turned chat off: no song either (REQ-NEARBY-TALK F-27).
        assertEquals(RideDecision.Nothing, decide(quietStretch.copy(chatReady = false, chatOn = false, songReady = true)))
    }

    @Test
    fun `the ride's first song comes before the other topics (REQ-KOYOMI F-42)`() {
        assertEquals(RideDecision.Chat(ChatTopic.SONG, 15 * SpeechBudget.CHARS_PER_SEC), decide(quietStretch.copy(songReady = true, songFirst = true)))
        assertEquals(RideDecision.Chat(ChatTopic.SEASON_WEATHER, 15 * SpeechBudget.CHARS_PER_SEC), decide(quietStretch.copy(songReady = true, songFirst = false)))
    }
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*RideDeciderTest"`
Expected: コンパイルエラー（`chatOn`・`songFirst` がない）

- [ ] **Step 3: `RideSituation` と規則を直す**

`RideDecider.kt` の `RideSituation` で、`chatReady` の次に足す（既定を `chatReady` にするので、今のテストと呼び出しはそのまま動く）:

```kotlin
    /** Whether the rider chats ([RideSettings.chat]), the AI or not: a season's song plays after its fixed word (platform REQ-KOYOMI F-43). */
    val chatOn: Boolean = chatReady,
```

`songReady` の次に足す:

```kotlin
    /** Whether the ride's first song is still to come: it doesn't wait its turn (platform REQ-KOYOMI F-42). */
    val songFirst: Boolean = false,
```

`Rule("chat", ...)` を次にする:

```kotlin
        // Nothing else to say for the gap: a word between, while riding, with the AI only; not near a
        // light or at speed, nor on a quiet channel (platform REQ-NEARBY-TALK F-14〜F-19). Without the AI
        // there is no chat, but a season's song still plays after its fixed word (REQ-KOYOMI F-43).
        Rule("chat", setOf(RideGate.CHAT_GAP, RideGate.NOT_QUIET, RideGate.NOT_SWITCHED)) { s ->
            if (s.state != MotionState.MOVING || s.level == TalkLevel.QUIET || TalkEventKind.CHAT !in s.events || !s.chatOn) return@Rule null
            // A chat the AI didn't answer isn't asked again at once.
            if (s.now - s.lastChatAt < s.values.ms(StyleItem.CHAT_GAP_MIN)) return@Rule null
            if (!s.chatReady) {
                return@Rule ChatTopic.SONG.takeIf { s.songReady && it !in s.chatTopics.takeLast(ChatTopic.NOT_AGAIN) }
                    ?.let { RideDecision.Chat(it, s.maxChars) }
            }
            ChatTopic.next(s.chatTopics, s.now - s.startedAt, s.nearbyKnown, s.songReady, s.songFirst)?.let { RideDecision.Chat(it, s.maxChars) }
        },
```

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*RideDeciderTest"`
Expected: PASS（既存の `chatReady = false` のテストは、`chatOn` も偽になるので `Nothing` のまま）

- [ ] **Step 5: `RideService` をつなぐ**

AI で雑談できるかの式を 1 か所にする。`RideService` に足す:

```kotlin
    /** Whether the rider chats and the AI can be asked (platform REQ-NEARBY-TALK F-17, F-19). */
    private fun aiChatReady(): Boolean =
        app.settings.settings.value.let { it.chat && (it.aiRoute == AiRoute.OPERATOR || it.apiKey().isNotBlank()) }
```

状況を作るところの `chatReady = app.settings.settings.value.let { ... },` を次にする:

```kotlin
                chatReady = aiChatReady(), chatOn = app.settings.settings.value.chat,
```

`songReady = songReady(s, station, now),` の次に足す:

```kotlin
                songFirst = s.songsPlayed == 0,
```

歌の前置き（`is RideDecision.Chat -> if (decision.topic == ChatTopic.SONG) { ... }` の中）の

```kotlin
                    talk(s, Prompts.Event.CHAT, chatVars(decision.topic, song.title), station, decision.maxChars)
                        ?: Line(Lines.song(SongMoment.seasonWord(SongSeason.now(now)), song.title))
```

を、AI が使えるときだけ AI に頼むようにする:

```kotlin
                    (if (aiChatReady()) talk(s, Prompts.Event.CHAT, chatVars(decision.topic, song.title), station, decision.maxChars) else null)
                        ?: Line(Lines.song(SongMoment.seasonWord(SongSeason.now(now)), song.title))
```

- [ ] **Step 6: 全体のテストとビルド**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/RideDecider.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ride/RideService.kt app/src/test/java/io/github/duwenji/yorisoi/cycleguide/companion/RideDeciderTest.kt
git commit -m "fix: AI が使えないときも季節の歌を定型の前置きで流す（REQ-KOYOMI F-43）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: 端末に曲がないときはすぐ取り、設定に曲の数を出す（CycleGuide、F-40・F-41）

**Files:**
- Create: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/SongShelf.kt`
- Create: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ways/SongFetchWorker.kt`
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/CycleApp.kt`（`songs = SongLibrary(...)` の後）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ui/SettingsScreen.kt`（`SettingsScreen` の引数、`ChatSection`）
- Modify: `app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ui/MainActivity.kt`（`SettingsScreen(...)` の呼び出し）
- Test: `app/src/test/java/io/github/duwenji/yorisoi/cycleguide/companion/SongShelfTest.kt`

**Interfaces:**
- Produces: `SongShelf.keptNow(library: SongLibrary, now: Long): Int`、`SongShelf.line(kept: Int): String`、`SongFetchWorker.ifNoneKept(context: Context, kept: Int)`、`SettingsScreen(..., songsLine: String = "")`

- [ ] **Step 1: 失敗するテストを書く**

`SongShelfTest.kt`（パッケージ名だけ SanpoGuide と違う）:

```kotlin
package io.github.duwenji.yorisoi.cycleguide.companion

import io.github.duwenji.yorisoi.kit.songs.SongLibrary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar

class SongShelfTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun song(id: String, season: String, bytes: Int) = """
        {"id":"$id","title":"$id","seasons":["$season"],"timesOfDay":[],"weather":[],"durationSec":30,
         "file":"$id.m4a","bytes":$bytes,"sha256":"${"0".repeat(64)}",
         "rights":{"lyricist":{"name":"a","died":1900},"composer":{"name":"b","died":1900},"score":"s"}}
    """.trimIndent()

    private fun library(vararg kept: Pair<String, Int>): SongLibrary {
        val dir = tmp.newFolder("songs")
        File(dir, "catalog.json").writeText(
            """{"format":"yorisoi-songs","version":1,"updatedAt":"2026-11-10T00:00:00Z","credit":"c","songs":[""" +
                listOf(song("momiji", "autumn", 10), song("hana", "spring", 10), song("furusato", "all", 10), song("akatombo", "autumn", 10)).joinToString(",") +
                "]}",
        )
        File(dir, "audio").mkdirs()
        kept.forEach { (id, size) -> File(dir, "audio/$id.m4a").writeBytes(ByteArray(size)) }
        return SongLibrary(dir, "https://example.invalid/", "test")
    }

    private val autumn = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 12, 0) }.timeInMillis

    @Test
    fun `counts the songs of the season now and of all seasons that are kept whole`() {
        assertEquals(2, SongShelf.keptNow(library("momiji" to 10, "furusato" to 10, "hana" to 10, "akatombo" to 3), autumn))
    }

    @Test
    fun `none before a catalog or a sound was fetched`() {
        assertEquals(0, SongShelf.keptNow(library(), autumn))
        assertEquals(0, SongShelf.keptNow(SongLibrary(tmp.newFolder("empty"), "https://example.invalid/", "test"), autumn))
    }

    @Test
    fun `the line under the switch (REQ-KOYOMI F-41)`() {
        assertEquals("今の季節の曲: 2 曲", SongShelf.line(2))
        assertEquals("まだ取っていません（Wi-Fi のときに取ります）", SongShelf.line(0))
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*SongShelfTest"`
Expected: コンパイルエラー（`SongShelf` がない）

- [ ] **Step 3: `SongShelf` を書く**

```kotlin
package io.github.duwenji.yorisoi.cycleguide.companion

import io.github.duwenji.yorisoi.kit.songs.SongLibrary
import io.github.duwenji.yorisoi.kit.songs.SongSeason

/** The season's songs on the device now (platform REQ-KOYOMI F-40, F-41). */
object SongShelf {
    /** How many songs of the season at [now] (or of all seasons) are kept whole on the device. */
    fun keptNow(library: SongLibrary, now: Long): Int {
        val season = SongSeason.now(now)
        return library.catalog?.songs.orEmpty().count { (season in it.seasons || "all" in it.seasons) && library.fileOf(it) != null }
    }

    /** The line under the settings' switch. */
    fun line(kept: Int): String = if (kept > 0) "今の季節の曲: $kept 曲" else "まだ取っていません（Wi-Fi のときに取ります）"
}
```

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests "*SongShelfTest"`
Expected: PASS

- [ ] **Step 5: `SongFetchWorker` を書く**

```kotlin
package io.github.duwenji.yorisoi.cycleguide.ways

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.duwenji.yorisoi.cycleguide.CycleApp
import io.github.duwenji.yorisoi.cycleguide.ride.RideService

/**
 * The season's songs when the device has none yet (platform REQ-KOYOMI F-40): once, as soon as the
 * network isn't metered, not waiting for the daily fetch ([WayPrefetchWorker]). Never during a ride
 * (API-SONGS AS-1): then it tries again later.
 */
class SongFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CycleApp
        val settings = app.settings.settings.value
        if (!settings.chat || !settings.playSongs) return Result.success()
        if (RideService.state.value != null) return Result.retry()
        if (!app.songs.refresh(force = app.songs.catalog == null)) return Result.retry()
        Log.i(TAG, "Fetched ${app.songs.prefetch()} songs")
        return Result.success()
    }

    companion object {
        private const val TAG = "SongFetch"
        private const val NAME = "songs-first"

        /** Asks for the songs once when [kept] (the season's songs on the device) is none. */
        fun ifNoneKept(context: Context, kept: Int) {
            if (kept > 0) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<SongFetchWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **Step 6: 起動時に呼ぶ**

`CycleApp.kt` の `songs = SongLibrary(...)` の次に足す（`SongShelf`・`SongFetchWorker` を import する）:

```kotlin
        settings.settings.value.let { if (it.chat && it.playSongs) SongFetchWorker.ifNoneKept(this, SongShelf.keptNow(songs, System.currentTimeMillis())) }
```

（`settings` のプロパティ名が `CycleApp` で違えば合わせる。`WayPrefetchWorker` が `app.settings.settings.value` を読んでいるのと同じもの）

- [ ] **Step 7: 設定に曲の数を出す**

`SettingsScreen.kt`:
- `fun SettingsScreen(` の引数の最後に `songsLine: String = "",` を足す
- `item { ChatSection(settings, onChange) }` を `item { ChatSection(settings, onChange, songsLine) }` にする
- `private fun ChatSection(settings: RideSettings, onChange: ((RideSettings) -> RideSettings) -> Unit)` を `private fun ChatSection(settings: RideSettings, onChange: ((RideSettings) -> RideSettings) -> Unit, songsLine: String)` にする
- 「季節の歌を流す」の `Row { ... Switch(...) }` の閉じかっこの次に足す:

```kotlin
        if (settings.chat && settings.playSongs && songsLine.isNotEmpty()) {
            Text(songsLine, style = MaterialTheme.typography.bodySmall)
        }
```

`MainActivity.kt` の `SettingsScreen(` の呼び出しに足す（`SongShelf` を import する）:

```kotlin
                            songsLine = SongShelf.line(SongShelf.keptNow(app.songs, System.currentTimeMillis())),
```

- [ ] **Step 8: 全体のテストとビルド**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: コミット**

```bash
git add app/src/main/java/io/github/duwenji/yorisoi/cycleguide/companion/SongShelf.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ways/SongFetchWorker.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/CycleApp.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ui/SettingsScreen.kt app/src/main/java/io/github/duwenji/yorisoi/cycleguide/ui/MainActivity.kt app/src/test/java/io/github/duwenji/yorisoi/cycleguide/companion/SongShelfTest.kt
git commit -m "fix: 端末に季節の歌がないときは Wi-Fi になりしだい取り、設定に曲の数を出す（REQ-KOYOMI F-40・F-41）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 終わったあと

- 両アプリの `fix/songs-play` を push して PR にする（push と PR は開発者の確認のあとで）
- 実機での確認は AC-9〜AC-12（入れたばかりのアプリを Wi-Fi につなぐと曲が入る、15 分を過ぎた最初の雑談で歌、AI なしでも歌、前の形の保存でも歌）。機内モードの確認（REQ-NEARBY-TALK AC-16）もやり直す
- REQ-NEARBY-TALK R-5（「歩き始めて 10 分は流さない」）は REQ-KOYOMI F-42 で 15 分になった。REQ-NEARBY-TALK に「REQ-KOYOMI F-42 で改めた」と書き足す（platform の `docs/req-koyomi` ブランチ）
