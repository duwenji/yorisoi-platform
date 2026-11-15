# チャンネルの形式に暦を足す（REQ-KOYOMI X-3）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** チャンネルが暦の雑談を止めたり、種類ごとに外したり先にしたりできるよう、チャンネルの形式に `talk.events.calendar` と `calendar.prefer`・`calendar.skip` を足す（REQ-KOYOMI F-20〜F-24）。

**Architecture:** platform の core-format に、暦の種類の語彙 `CalendarKind`（JSON の綴りは kit-calendar の `KoyomiKind.json` と同じ）、出来事 `TalkEventKind.CALENDAR`、`StationManifest.calendarPrefer`・`calendarSkip` を足し、`StationValidator` で読む。kit-channel の `Station` から読めるようにする。契約書 API-CHANNEL-PACKAGE を改める。core-format のマイナー版（platform 2.33.0）で、`format` は上げない。core-format は kit-calendar に依存しない（アプリが `json` の綴りで両方をつなぐ）。

**Tech Stack:** Kotlin（JVM 17）、org.json、JUnit 4

**Spec:** `yorisoi-platform/docs/requirements/REQ-KOYOMI.md`（D-9、F-20〜F-24、AC-5〜AC-7）、`yorisoi-platform/docs/contracts/API-CHANNEL-PACKAGE.md`（G-2・G-3・G-5、「アプリでの知らない値」、「互換性方針・移行メモ」）

## Global Constraints

- 暦の種類の値（JSON の綴り）: `sekki`・`kou`・`zassetsu`・`sekku`・`lunar`・`holiday`・`moon`・`rokuyou`・`kinenbi`・`regional`（kit-calendar の `KoyomiKind.json` と同じ並び・綴り）
- `talk.events.calendar` は任意で、書かなければ真。`chat` が偽なら `calendar` も偽（`song` と同じ。F-20）
- `calendar` は最上位の任意の項目。`prefer`・`skip` はどちらも任意で、それぞれ 0〜10 個、重複なし。同じ種類を両方に書いたら拒む（F-21〜F-23）
- 知らない種類: 審査（`Strictness.REVIEW`）は拒む（`bad_value`）。アプリ（`Strictness.APP`）はその値を無視し、チャンネルは使う（制限が緩まないため。G-5）
- `format` は上げない。platform の版は 2.33.0（core-format の語彙を足すマイナー版。F-24）
- コミットのメッセージは日本語、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- platform の `feat/channel-calendar` ブランチを main から切って作業する
- ビルドは `JAVA_HOME` を JDK 17 にして行う（この PC では `~/.jdks/jdk-17.0.20.1+1`）

## Review Focus

1. `calendar` を書かない今のチャンネル（組み込みも第三者も）→ 今までどおり読め、`calendarPrefer`・`calendarSkip` が空、`talk.events` に `CALENDAR` が入ること（Task 1 のテスト）
2. 新しいアプリが知らない暦の種類（将来足す値）を `skip` に書いたチャンネル → アプリではチャンネルを使い、その値だけ無視すること（Task 1 のテスト）
3. `calendar` が配列でない・中身が文字列でない → `bad_manifest` で拒むこと（Task 1 のテスト）
4. `chat` が偽で `calendar` が真と書いたチャンネル → 暦も出ないこと（Task 1 のテスト）
5. core-format の `CalendarKind` と kit-calendar の `KoyomiKind` の綴りがずれる → テストで気づくこと（Task 2 のテスト）

---

テストの実行: `cd yorisoi-platform && ./gradlew :core-format:test :kit-channel:testDebugUnitTest`

### Task 1: core-format に暦の項目を足す（F-20〜F-23）

**Files:**
- Modify: `core-format/src/main/kotlin/io/github/duwenji/yorisoi/core/format/StationManifest.kt`（`TalkEventKind.CALENDAR`、`CalendarKind`、`StationManifest.calendarPrefer`・`calendarSkip`）
- Modify: `core-format/src/main/kotlin/io/github/duwenji/yorisoi/core/format/StationValidator.kt`（`readManifest`）
- Modify: `core-format/src/main/kotlin/io/github/duwenji/yorisoi/core/format/Slot.kt`（`forEvent`）
- Test: `core-format/src/test/kotlin/io/github/duwenji/yorisoi/core/format/StationValidatorTest.kt`

**Interfaces:**
- Produces: `enum class CalendarKind(override val json: String) : JsonValue { SEKKI("sekki"), KOU("kou"), ZASSETSU("zassetsu"), SEKKU("sekku"), LUNAR("lunar"), HOLIDAY("holiday"), MOON("moon"), ROKUYOU("rokuyou"), KINENBI("kinenbi"), REGIONAL("regional") }`、`TalkEventKind.CALENDAR("calendar", required = false)`、`StationManifest.calendarPrefer: List<CalendarKind> = emptyList()`・`calendarSkip: List<CalendarKind> = emptyList()`

- [ ] **Step 1: ブランチを切る**

```bash
cd yorisoi-platform && git checkout main && git pull && git checkout -b feat/channel-calendar
```

- [ ] **Step 2: 失敗するテストを書く**

`StationValidatorTest.kt` の `the preview, chat and song events are on unless a channel turns them off` の次に足す。`check` を `Strictness.APP` で呼ぶ形は、このファイルの既存のテスト（`strictness = Strictness.APP` を渡しているもの）に合わせる。見つからなければ `StationValidator.check(files, StationOrigin.BUILT_IN, strictness = Strictness.APP)` と書く。

```kotlin
    private fun ok(files: StationFiles, strictness: Strictness = Strictness.REVIEW) =
        (StationValidator.check(files, StationOrigin.BUILT_IN, strictness = strictness) as StationCheck.Ok).station.manifest

    private fun calendar(prefer: List<String> = emptyList(), skip: List<String> = emptyList()) =
        manifest { put("calendar", JSONObject(mapOf("prefer" to JSONArray(prefer), "skip" to JSONArray(skip)))) }

    @Test
    fun `the calendar event is on unless a channel turns it off, and off with chat (REQ-KOYOMI F-20)`() {
        assertTrue(TalkEventKind.CALENDAR in ok(files()).events)
        val off = manifest { getJSONObject("talk").getJSONObject("events").put("calendar", false) }
        assertTrue(TalkEventKind.CALENDAR !in ok(files(off)).events)
        val noChat = manifest { getJSONObject("talk").getJSONObject("events").put("chat", false).put("calendar", true) }
        assertTrue(TalkEventKind.CALENDAR !in ok(files(noChat)).events)
    }

    @Test
    fun `a channel without calendar reads as before (REQ-KOYOMI F-24)`() {
        val m = ok(files())
        assertEquals(emptyList<CalendarKind>(), m.calendarPrefer)
        assertEquals(emptyList<CalendarKind>(), m.calendarSkip)
    }

    @Test
    fun `calendar kinds to put first and to leave out (REQ-KOYOMI F-21, F-22)`() {
        val m = ok(files(calendar(prefer = listOf("kou", "sekki"), skip = listOf("rokuyou", "kinenbi"))))
        assertEquals(listOf(CalendarKind.KOU, CalendarKind.SEKKI), m.calendarPrefer)
        assertEquals(listOf(CalendarKind.ROKUYOU, CalendarKind.KINENBI), m.calendarSkip)
        // Either may be left out.
        assertEquals(listOf(CalendarKind.ROKUYOU), ok(files(manifest { put("calendar", JSONObject(mapOf("skip" to JSONArray(listOf("rokuyou"))))) })).calendarSkip)
    }

    @Test
    fun `a calendar kind both preferred and skipped, repeated, unknown or misshapen is turned away in review (REQ-KOYOMI F-23)`() {
        assertEquals(RejectCode.BAD_VALUE, rejected(files(calendar(prefer = listOf("kou"), skip = listOf("kou")))))
        assertEquals(RejectCode.BAD_VALUE, rejected(files(calendar(skip = listOf("rokuyou", "rokuyou")))))
        assertEquals(RejectCode.BAD_VALUE, rejected(files(calendar(skip = listOf("horoscope")))))
        assertEquals(RejectCode.BAD_VALUE, rejected(files(calendar(prefer = CalendarKind.entries.map { it.json } + "sekki"))))
        assertEquals(RejectCode.BAD_MANIFEST, rejected(files(manifest { put("calendar", JSONObject(mapOf("skip" to "rokuyou"))) })))
        assertEquals(RejectCode.BAD_MANIFEST, rejected(files(manifest { put("calendar", "rokuyou") })))
    }

    @Test
    fun `an app ignores a calendar kind it doesn't know and still uses the channel (G-5)`() {
        val m = ok(files(calendar(skip = listOf("rokuyou", "horoscope"))), Strictness.APP)
        assertEquals(listOf(CalendarKind.ROKUYOU), m.calendarSkip)
        assertEquals(emptyList<String>(), m.unknownRestrictions)
    }
```

（重複の確かめに 10 個を超える例を使う 4 行目は、10 種類すべてに `sekki` を足して 11 個にしている。`distinctOrReject` が先に効いて `bad_value` になるが、どちらでも `bad_value` なので期待は同じ）

- [ ] **Step 3: 失敗を確かめる**

Run: `./gradlew :core-format:test`
Expected: コンパイルエラー（`CalendarKind`・`calendarPrefer`・`TalkEventKind.CALENDAR` がない）

- [ ] **Step 4: `StationManifest.kt` を直す**

`TalkEventKind` の `SONG(...)` の次に足す:

```kotlin

    /** A word on the calendar (a solar term, a festival…), as one of the chat's topics; off with [CHAT] too (REQ-KOYOMI F-20). */
    CALENDAR("calendar", required = false),
```

（`SONG("song", required = false),` の行末のカンマはそのまま。`CALENDAR` の行が最後になる）

`SoundChoice` の次に足す:

```kotlin
/**
 * The kinds of the calendar's facts a channel can put first or leave out (REQ-KOYOMI「暦の種類」,
 * F-21, F-22). Spelled as kit-calendar's `KoyomiKind.json`; the app matches the two by that spelling.
 */
enum class CalendarKind(override val json: String) : JsonValue {
    SEKKI("sekki"), KOU("kou"), ZASSETSU("zassetsu"), SEKKU("sekku"), LUNAR("lunar"),
    HOLIDAY("holiday"), MOON("moon"), ROKUYOU("rokuyou"), KINENBI("kinenbi"), REGIONAL("regional"),
}
```

`StationManifest` の `sound` の次に足す:

```kotlin
    /** Calendar kinds to bring up first, and ones not to bring up (`calendar.prefer`・`calendar.skip`; REQ-KOYOMI F-21, F-22). */
    val calendarPrefer: List<CalendarKind> = emptyList(),
    val calendarSkip: List<CalendarKind> = emptyList(),
```

- [ ] **Step 5: `StationValidator.kt` を直す**

`readManifest` の `val sound = …` の次に足す:

```kotlin
        // calendar (REQ-KOYOMI F-21〜F-23): optional; a kind the app doesn't know only drops out, as an
        // app that doesn't know it never tells it, so leaving it out loosens nothing (G-5).
        val calendar = json.optNullable("calendar")?.let { it as? JSONObject ?: reject(RejectCode.BAD_MANIFEST, "calendar is not an object") }
        fun calendarKinds(key: String): List<CalendarKind> {
            val written = (calendar?.optList(key) ?: emptyList()).strings("calendar.$key", RejectCode.BAD_MANIFEST).distinctOrReject("calendar.$key")
            if (written.size > 10) reject(RejectCode.BAD_VALUE, "calendar.$key has ${written.size} kinds (max 10)")
            return written.mapNotNull { parseEnum<CalendarKind>(it) ?: unknown("calendar.$key", it, restricts = false) }
        }
        val calendarPrefer = calendarKinds("prefer")
        val calendarSkip = calendarKinds("skip")
        (calendarPrefer intersect calendarSkip.toSet()).firstOrNull()?.let { reject(RejectCode.BAD_VALUE, "calendar ${it.json} is both preferred and skipped") }
```

`StationManifest(` の呼び出しで、`events = …` の 2 行を次にする（`chat` が偽なら `song` と `calendar` も外す）:

```kotlin
            events = TalkEventKind.entries.filter { if (it.required || events.optNullable(it.json) != null) events.bool(it.json) else true }
                .let { if (TalkEventKind.CHAT in it) it else it - TalkEventKind.SONG - TalkEventKind.CALENDAR }.toSet(),
```

`sound = sound,` の次に `calendarPrefer = calendarPrefer, calendarSkip = calendarSkip,` を足す。

`optList` が `JSONObject` の拡張として `Checks.kt` にあることを確かめる（`json.optList("genres")` と同じもの）。`calendar.optList(key)` に配列でない値があると `optList` が `bad_manifest` で拒むことを、テストの 5 行目が確かめる。拒まないなら、`optList` の振る舞いを読んで、`calendarKinds` の中で配列かを確かめる。

- [ ] **Step 6: `Slot.kt` を直す**

`forEvent` の `TalkEventKind.PREVIEW, TalkEventKind.CHAT, TalkEventKind.SONG -> null` を次にする:

```kotlin
            TalkEventKind.PREVIEW, TalkEventKind.CHAT, TalkEventKind.SONG, TalkEventKind.CALENDAR -> null
```

- [ ] **Step 7: 通ることを確かめる**

Run: `./gradlew :core-format:test`
Expected: PASS（既存の `the preview, chat and song events …` も通る。`events()` に `CALENDAR` が増えるが、そのテストは `containsAll` と `intersect` で見ているので影響しない）

- [ ] **Step 8: コミット**

```bash
git add core-format
git commit -m "feat(core-format): チャンネルの形式に暦の出来事と calendar.prefer・calendar.skip を足す（REQ-KOYOMI F-20〜F-23）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: kit-channel の Station から読めるようにし、kit-calendar と綴りをそろえる（F-21・F-22）

**Files:**
- Modify: `kit-channel/src/main/kotlin/io/github/duwenji/yorisoi/kit/channel/Station.kt`
- Modify: `kit-calendar/build.gradle.kts`（テストだけ core-format を見る）
- Test: `kit-calendar/src/test/kotlin/io/github/duwenji/yorisoi/kit/calendar/CalendarKindsTest.kt`
- Modify: `kit-channel/src/test/resources/test-channels/quiet/channel.json`
- Test: `kit-channel/src/test/kotlin/io/github/duwenji/yorisoi/kit/channel/StationTest.kt`

**Interfaces:**
- Consumes: `CalendarKind`、`StationManifest.calendarPrefer`・`calendarSkip`（Task 1）
- Produces: `Station.calendarPrefer: Set<CalendarKind>`、`Station.calendarSkip: Set<CalendarKind>`（チャンネルの値そのまま。利用者の変更はない）

- [ ] **Step 1: 失敗するテストを書く**

`kit-calendar/src/test/kotlin/io/github/duwenji/yorisoi/kit/calendar/CalendarKindsTest.kt`:

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import io.github.duwenji.yorisoi.core.format.CalendarKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarKindsTest {
    @Test
    fun `the channel format's calendar kinds are spelled as the calendar's (REQ-KOYOMI F-21)`() {
        assertEquals(KoyomiKind.entries.map { it.json }, CalendarKind.entries.map { it.json })
    }
}
```

`kit-calendar/build.gradle.kts` の `dependencies` に足す:

```kotlin
    // Only to check the channel format spells the kinds the same way (REQ-KOYOMI F-21).
    testImplementation(project(":core-format"))
```

`kit-channel/src/test/resources/test-channels/quiet/channel.json` の `"mood": { … }` の行の後に、`calendar` を足す（`mood` の行の末尾にカンマを足す）:

```json
  "mood": { "tone": true, "sound": "auto" },
  "calendar": { "prefer": ["kou"], "skip": ["rokuyou"] }
```

`kit-channel/src/test/kotlin/io/github/duwenji/yorisoi/kit/channel/StationTest.kt` に足す（`standard`・`quiet` は、このクラスの `TestChannels.load()` で読んだもの）:

```kotlin
    @Test
    fun `a channel's calendar kinds to put first and to leave out (REQ-KOYOMI F-21, F-22)`() {
        assertEquals(setOf(CalendarKind.KOU), quiet.calendarPrefer)
        assertEquals(setOf(CalendarKind.ROKUYOU), quiet.calendarSkip)
        // A channel without calendar has none.
        assertEquals(emptySet<CalendarKind>(), standard.calendarPrefer)
        assertEquals(emptySet<CalendarKind>(), standard.calendarSkip)
    }
```

import に `io.github.duwenji.yorisoi.core.format.CalendarKind` を足す。

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test :kit-channel:testDebugUnitTest`
Expected: kit-channel はコンパイルエラー（`calendarPrefer` がない）。kit-calendar の `CalendarKindsTest` は、Task 1 で綴りをそろえてあれば PASS（このテストは、これからずれたときに気づくためのもの）

- [ ] **Step 3: `Station.kt` を直す**

`val searchedKinds: …` の次に足す:

```kotlin

    /** Calendar kinds to bring up first, and ones not to bring up (REQ-KOYOMI F-21, F-22). The user doesn't change these. */
    val calendarPrefer: Set<CalendarKind> get() = manifest.calendarPrefer.toSet()
    val calendarSkip: Set<CalendarKind> get() = manifest.calendarSkip.toSet()
```

import に `io.github.duwenji.yorisoi.core.format.CalendarKind` を足す。

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test :kit-channel:testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add kit-channel kit-calendar
git commit -m "feat(kit-channel): チャンネルの暦の種類（先にする・外す）を Station から読む（REQ-KOYOMI F-21・F-22）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: 契約書と版（F-24）

**Files:**
- Modify: `docs/contracts/API-CHANNEL-PACKAGE.md`
- Modify: `gradle.properties`（`yorisoiVersion=2.33.0`）

- [ ] **Step 1: API-CHANNEL-PACKAGE を改める**

1. 「channel.json」の項目の表の `talk.events` の行で、「任意の 3 つ」を「任意の 4 つ」にし、`song` の説明の後に「・`calendar`（雑談の話題の 1 つとしての暦。`chat` が偽なら出さない）（REQ-KOYOMI）」を足す
2. 同じ表の `spots.skip` の行の次に 2 行を足す:

```markdown
| `calendar.prefer` | string[] | 任意 | 暦の雑談で先に話す暦の種類（[暦の種類](#暦の種類)）。0〜10 個（REQ-KOYOMI F-22） |
| `calendar.skip` | string[] | 任意 | 暦の雑談で話さない暦の種類。0〜10 個。`prefer` と重ねない（REQ-KOYOMI F-21・F-23） |
```

3. 「スポットの種類」の節の後に、新しい節を足す:

```markdown
### 暦の種類

`calendar.prefer`・`calendar.skip` に書ける値（REQ-KOYOMI「暦の種類」）。値を足すのは `core-format` のマイナー版。

| 値 | 種類 |
|---|---|
| `sekki` | 二十四節気 |
| `kou` | 七十二候 |
| `zassetsu` | 雑節（節分・彼岸・八十八夜・入梅・土用など） |
| `sekku` | 五節句・年中行事（地域で違わないもの） |
| `lunar` | 旧暦の行事（十五夜・十三夜） |
| `holiday` | 国民の祝日 |
| `moon` | 月の満ち欠け |
| `rokuyou` | 六曜 |
| `kinenbi` | 記念日（国・自治体・団体が定めたもの） |
| `regional` | 地域ごとの行事（お盆の時期・地蔵盆など） |
```

4. 「アプリでの知らない値」の表の `spots.prefer` の行の次に足す:

```markdown
| `calendar.prefer`・`calendar.skip` | その値を無視する（知らない種類の暦は、そのアプリはもともと話さないので、制限は緩まない） |
```

5. 「channel.json」の例（`"talk": { … }` のある JSON）は変えない（任意の項目なので、今の例のままで正しい）
6. 「確認観点」の表に、次の観点を足す（今の最後の番号の次から振る）:

```markdown
| V-xx | `calendar.prefer`・`calendar.skip` に同じ種類・知らない種類・11 個以上を書いたチャンネルを、審査で拒む（`bad_value`）。アプリは知らない種類だけを無視してチャンネルを使う |
| V-xx | `talk.events.chat` が偽のチャンネルでは、`calendar` が真でも暦の雑談を出さない |
```

（`V-xx` は、表の今の最後の番号に続けて振る）

- [ ] **Step 2: 版を上げる**

`gradle.properties` の `yorisoiVersion=2.32.0` を `yorisoiVersion=2.33.0` にする。

- [ ] **Step 3: platform 全体のテスト**

Run: `./gradlew test --continue`
Expected: kit-local-materials 以外はすべて通る（kit-local-materials の 4 件は、この PC では改行の変換で落ちる。前の計画の ledger の Task 7 の Ruling と同じ。CI で確かめる）

- [ ] **Step 4: コミット**

```bash
git add docs/contracts/API-CHANNEL-PACKAGE.md gradle.properties
git commit -m "docs: API-CHANNEL-PACKAGE に暦の出来事と calendar を足す（REQ-KOYOMI F-24、v2.33.0）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 終わったあと

- push・PR・マージのあとにタグ `v2.33.0` を付けて公開する（開発者の確認のあとで）
- 両アプリで 2.33.0 に上げると、`TalkEventKind.entries` に `calendar` が増える。SanpoGuide の `StoredNamesTest`（保存の名前の一覧を確かめている）は、そのとき一緒に直す（X-5）
- yorisoi-console の確認の仕組み（TypeScript）とエディタは X-4 で同じ値に追従させる
