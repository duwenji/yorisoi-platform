# kit-calendar（REQ-KOYOMI X-2）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 日付を渡すと、その日に話せる暦の事実の一覧を返す platform の新しい部品 `kit-calendar` を作る（REQ-KOYOMI F-1〜F-8）。

**Architecture:** Android に依存しない Kotlin/JVM のモジュール。天文の計算（節気の時刻、新月・上弦・満月・下弦の時刻）は Astronomy Engine に任せ、その上に、節気・七十二候・旧暦（六曜・十五夜）・雑節・祝日・表（五節句と年中行事・記念日・地域ごとの行事）を積む。`Koyomi.on(date)` がそれらを集め、F-4 の決まりで「今日・〇日目・あと〇日・昨日」を付けて返す。正しさは、国立天文台の暦要項（2016〜2027 年）と内閣府の祝日の一覧とテストで照らす。

**Tech Stack:** Kotlin 2.2（JVM 17）、java.time、Astronomy Engine 2.1.19（`io.github.cosinekitty:astronomy`、JitPack）、JUnit 4、Python 3 と pdftotext（テストの表を作る道具だけ）

**Spec:** `yorisoi-platform/docs/requirements/REQ-KOYOMI.md`（D-1〜D-7・D-10、「暦の種類」、F-1〜F-8、N-3〜N-5、AC-1〜AC-3）

## Global Constraints

- 日付の区切りは日本時間（UTC+9。夏時間はない）
- 天文の計算は Astronomy Engine 2.1.19。JitPack から取るのはこのライブラリの group（`io.github.cosinekitty`）だけ（Gradle の `exclusiveContent`）。版は固定する
- 実行時に通信しない（N-1）
- 表は合わせて 200 KB 以内（N-4）
- 「暦の種類」の値（JSON の綴り）: `sekki`・`kou`・`zassetsu`・`sekku`・`lunar`・`holiday`・`moon`・`rokuyou`・`kinenbi`・`regional`
- 確かさの種類: 天文・法律（`sekki`・`holiday`・`moon`）、言い伝え（`kou`・`zassetsu`・`sekku`・`lunar`・`rokuyou`）、定めた人がいる（`kinenbi`）、地域で違う（`regional`）
- 記念日は、国・自治体・業界団体・公益団体・国際機関が定めたものだけ。商品名・社名の付いたものは入れない（D-5）。説明の文は自分で書く（資料の文を写さない）
- 六曜の説明に「〇〇を避ける」「〇〇に向く」を書かない（D-4）
- 祝日は 2016 年以降の祝日法の決まりで計算する（山の日ができた年から）
- コミットのメッセージは日本語、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- platform の `feat/kit-calendar` ブランチを main から切って作業する
- ビルドは `JAVA_HOME` を JDK 17 にして行う（この PC では `~/.jdks/jdk-17.0.20.1+1`）

## Review Focus

1. 1 月 1〜4 日（今の節気が前の年の冬至）→ 前の年の冬至から数えた日数が出るべき（Task 7 のテスト）
2. 12 月の末に、あと 1〜3 日で次の年の行事・祝日（元日など）→「あと〇日」で出るべき（Task 7 のテスト）
3. 閏月のある年（2025 年の閏 6 月）→ 旧暦の月と閏が正しく、十五夜・六曜がずれないこと（Task 3 のテスト）
4. 節気・朔が日本時間の真夜中のすぐ前後（2025 年の冬至 0 時 03 分、2027 年の立冬 0 時 39 分）→ 日付が暦要項と同じであること（Task 1・2 のテスト）
5. 2019〜2021 年の特別な祝日（即位の日・オリンピックの移動）と 10 連休の国民の休日 → 内閣府の一覧と同じであること（Task 5 のテスト）

---

テストの実行: `cd yorisoi-platform && ./gradlew :kit-calendar:test`（JAVA_HOME は JDK 17）。結果は `kit-calendar/build/test-results/test/` に出る。

パッケージ: `io.github.duwenji.yorisoi.kit.calendar`（以下 `<pkg>` = `kit-calendar/src/main/kotlin/io/github/duwenji/yorisoi/kit/calendar`、`<test>` = `kit-calendar/src/test/kotlin/io/github/duwenji/yorisoi/kit/calendar`）

### Task 1: モジュールを作り、二十四節気の時刻を出す（F-1・F-5、D-1）

**Files:**
- Modify: `settings.gradle.kts`（`include(":kit-calendar")`、JitPack の `exclusiveContent`）
- Modify: `gradle/libs.versions.toml`（`astronomy`）
- Create: `kit-calendar/build.gradle.kts`
- Create: `<pkg>/Astro.kt`、`<pkg>/SolarTerm.kt`
- Test: `<test>/SolarTermsTest.kt`

**Interfaces:**
- Produces: `internal object Astro { val JST: ZoneOffset; fun startOf(year: Int): Instant; fun dateOf(at: Instant): LocalDate; fun sunSteps(firstLon: Double, step: Double, count: Int, from: Instant): List<Instant>; fun sunAt(lon: Double, year: Int): Instant; fun moonQuarters(from: Instant, until: Instant): List<Pair<Int, Instant>> }`、`enum class SolarTerm(key, label, reading, longitude: Int, note) { …; val isChuki: Boolean }`、`object SolarTerms { fun of(year: Int): List<Pair<SolarTerm, Instant>>; fun dateOf(term: SolarTerm, year: Int): LocalDate }`

- [ ] **Step 1: ブランチを切る**

```bash
cd yorisoi-platform && git checkout main && git pull && git checkout -b feat/kit-calendar
```

- [ ] **Step 2: モジュールの骨組み（ビルドの設定はテストの前に要る）**

`settings.gradle.kts` の `dependencyResolutionManagement { repositories { … } }` を次にし、`include(":kit-songs")` の次に `include(":kit-calendar")` を足す:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Astronomy Engine is only on JitPack; nothing else is taken from there (REQ-KOYOMI D-1).
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("io.github.cosinekitty") }
        }
    }
}
```

`gradle/libs.versions.toml` の `[versions]` に `astronomy = "2.1.19"`、`[libraries]` に `astronomy = { module = "io.github.cosinekitty:astronomy", version.ref = "astronomy" }` を足す。

`kit-calendar/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The calendar's facts for a day (REQ-KOYOMI): the 24 solar terms and the 72 pentads, the old
// lunisolar calendar (rokuyou, the harvest moon), the seasonal days, the national holidays, the
// moon's quarters, and the tables of festivals and designated days. Plain JVM; no network.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "The calendar's facts for a day: solar terms, pentads, the lunisolar calendar, seasonal days, holidays, moon phases."

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.astronomy)
    testImplementation(libs.junit)
}
```

- [ ] **Step 3: 失敗するテストを書く**

`<test>/SolarTermsTest.kt`（暦要項 2025〜2027 年の HTML の値。https://eco.mtk.nao.ac.jp/koyomi/yoko/{年}/rekiyou{年の下 2 桁}2.html、2026-11-15 に取った）:

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class SolarTermsTest {
    /** year, longitude, month, day, hour, minute: 暦要項 (国立天文台) 2025–2027, 二十四節気. */
    private val rekiyou = """
        2025 285 1 5 11 33|2025 300 1 20 5 0|2025 315 2 3 23 10|2025 330 2 18 19 7|2025 345 3 5 17 7|2025 0 3 20 18 1
        2025 15 4 4 21 49|2025 30 4 20 4 56|2025 45 5 5 14 57|2025 60 5 21 3 55|2025 75 6 5 18 57|2025 90 6 21 11 42
        2025 105 7 7 5 5|2025 120 7 22 22 29|2025 135 8 7 14 52|2025 150 8 23 5 34|2025 165 9 7 17 52|2025 180 9 23 3 19
        2025 195 10 8 9 41|2025 210 10 23 12 51|2025 225 11 7 13 4|2025 240 11 22 10 36|2025 255 12 7 6 5|2025 270 12 22 0 3
        2026 285 1 5 17 23|2026 300 1 20 10 45|2026 315 2 4 5 2|2026 330 2 19 0 52|2026 345 3 5 22 59|2026 0 3 20 23 46
        2026 15 4 5 3 40|2026 30 4 20 10 39|2026 45 5 5 20 49|2026 60 5 21 9 37|2026 75 6 6 0 48|2026 90 6 21 17 25
        2026 105 7 7 10 57|2026 120 7 23 4 13|2026 135 8 7 20 43|2026 150 8 23 11 19|2026 165 9 7 23 41|2026 180 9 23 9 5
        2026 195 10 8 15 29|2026 210 10 23 18 38|2026 225 11 7 18 52|2026 240 11 22 16 23|2026 255 12 7 11 53|2026 270 12 22 5 50
        2027 285 1 5 23 10|2027 300 1 20 16 30|2027 315 2 4 10 46|2027 330 2 19 6 33|2027 345 3 6 4 40|2027 0 3 21 5 25
        2027 15 4 5 9 17|2027 30 4 20 16 18|2027 45 5 6 2 25|2027 60 5 21 15 18|2027 75 6 6 6 26|2027 90 6 21 23 11
        2027 105 7 7 16 37|2027 120 7 23 10 5|2027 135 8 8 2 27|2027 150 8 23 17 14|2027 165 9 8 5 28|2027 180 9 23 15 2
        2027 195 10 8 21 17|2027 210 10 24 0 33|2027 225 11 8 0 39|2027 240 11 22 22 16|2027 255 12 7 17 38|2027 270 12 22 11 42
    """.trimIndent().split('\n', '|').map { it.trim().split(' ').map(String::toInt) }

    @Test
    fun `the 24 terms of 2025-2027 fall on 暦要項's dates, within 2 minutes of its moments (AC-1)`() {
        for (v in rekiyou) {
            val (y, lon, m, d, h) = v
            val mi = v[5]
            val (term, at) = SolarTerms.of(y).single { it.first.longitude == lon }
            val ours = at.atOffset(Astro.JST).toLocalDateTime()
            val theirs = LocalDateTime.of(y, m, d, h, mi)
            assertEquals("${term.label} $y", theirs.toLocalDate(), ours.toLocalDate())
            assertTrue("${term.label} $y: $ours vs $theirs", Duration.between(theirs, ours).abs().toMinutes() <= 2)
        }
    }

    @Test
    fun `a year's terms go from 小寒 to 冬至, 中気 every other one`() {
        val terms = SolarTerms.of(2026).map { it.first }
        assertEquals(SolarTerm.entries, terms)
        assertEquals(listOf("大寒", "雨水", "春分", "穀雨", "小満", "夏至", "大暑", "処暑", "秋分", "霜降", "小雪", "冬至"), terms.filter { it.isChuki }.map { it.label })
        assertEquals(LocalDate.of(2026, 11, 7), SolarTerms.dateOf(SolarTerm.RITTOU, 2026))
    }

}
```

- [ ] **Step 4: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: コンパイルエラー（`SolarTerms`・`Astro` がない）。JitPack から `astronomy-2.1.19` を取れずに失敗したときは、ネットワークか `exclusiveContent` の書き方を直す（テストの失敗とは別）

- [ ] **Step 5: `Astro.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchMoonQuarter
import io.github.cosinekitty.astronomy.searchSunLongitude
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The astronomy the calendar stands on, by Astronomy Engine (REQ-KOYOMI D-1): when the Sun's
 * apparent ecliptic longitude reaches a value, and the Moon's quarters (the difference between the
 * Moon's and the Sun's ecliptic longitudes: 0 new, 90 first quarter, 180 full, 270 last quarter).
 */
internal object Astro {
    /** Japan's time; the calendar's days are counted in it. */
    val JST: ZoneOffset = ZoneOffset.ofHours(9)

    private fun time(at: Instant) = Time.fromMillisecondsSince1970(at.toEpochMilli())
    private fun instant(t: Time): Instant = Instant.ofEpochMilli(t.toMillisecondsSince1970())

    fun startOf(year: Int): Instant = LocalDate.of(year, 1, 1).atStartOfDay().toInstant(JST)

    fun dateOf(at: Instant): LocalDate = at.atOffset(JST).toLocalDate()

    /** The moments the Sun reaches [firstLon], then each [step] degrees on, [count] of them, from [from] on. */
    fun sunSteps(firstLon: Double, step: Double, count: Int, from: Instant): List<Instant> {
        val out = ArrayList<Instant>(count)
        var after = from
        repeat(count) { k ->
            val lon = (firstLon + step * k) % 360.0
            val found = searchSunLongitude(lon, time(after), step * 1.3 + 3.0)
                ?: error("The Sun doesn't reach $lon° soon after $after")
            out += instant(found)
            after = instant(found).plusSeconds(3600)
        }
        return out
    }

    /** The moment in [year] (Japan's) the Sun reaches [lon]. */
    fun sunAt(lon: Double, year: Int): Instant {
        var after = startOf(year)
        repeat(13) {
            searchSunLongitude(lon, time(after), 30.0)?.let { return instant(it) }
            after = after.plus(Duration.ofDays(30))
        }
        error("The Sun doesn't reach $lon° in $year")
    }

    /** The Moon's quarters (0 new, 1 first, 2 full, 3 last) from [from] until [until]. */
    fun moonQuarters(from: Instant, until: Instant): List<Pair<Int, Instant>> {
        val out = mutableListOf<Pair<Int, Instant>>()
        var mq = searchMoonQuarter(time(from))
        while (instant(mq.time).isBefore(until)) {
            out += mq.quarter to instant(mq.time)
            mq = nextMoonQuarter(mq)
        }
        return out
    }
}
```

- [ ] **Step 6: `SolarTerm.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** The 24 solar terms in the order of a year, 小寒 first; [note] is the line told with it (REQ-KOYOMI D-3). */
enum class SolarTerm(val key: String, val label: String, val reading: String, val longitude: Int, val note: String) {
    SHOUKAN("shoukan", "小寒", "しょうかん", 285, "寒の入り。ここから節分までが「寒の内」で、寒さが厳しくなるころ"),
    DAIKAN("daikan", "大寒", "だいかん", 300, "一年でいちばん寒さが厳しいころとされる"),
    RISSHUN("risshun", "立春", "りっしゅん", 315, "暦の上で春が始まる日。八十八夜や二百十日は、この日から数える"),
    USUI("usui", "雨水", "うすい", 330, "雪が雨に変わり、氷が解けて水になるころとされる"),
    KEICHITSU("keichitsu", "啓蟄", "けいちつ", 345, "冬ごもりの虫が土の中から出てくるころとされる"),
    SHUNBUN("shunbun", "春分", "しゅんぶん", 0, "昼と夜の長さがほぼ同じになる日。春の彼岸の中日"),
    SEIMEI("seimei", "清明", "せいめい", 15, "万物がすがすがしく明るく美しいころとされる"),
    KOKUU("kokuu", "穀雨", "こくう", 30, "穀物を潤す春の雨が降るころとされる"),
    RIKKA("rikka", "立夏", "りっか", 45, "暦の上で夏が始まる日"),
    SHOUMAN("shouman", "小満", "しょうまん", 60, "草木が茂り、天地に生気が満ち始めるころとされる"),
    BOUSHU("boushu", "芒種", "ぼうしゅ", 75, "稲や麦など、穂の出る穀物の種をまくころとされる"),
    GESHI("geshi", "夏至", "げし", 90, "一年で昼がいちばん長い日"),
    SHOUSHO("shousho", "小暑", "しょうしょ", 105, "本格的な暑さが始まるころ。暑中見舞いはこのころから"),
    TAISHO("taisho", "大暑", "たいしょ", 120, "一年でいちばん暑さが厳しいころとされる"),
    RISSHUU("risshuu", "立秋", "りっしゅう", 135, "暦の上で秋が始まる日。これより後の暑さは残暑と呼ぶ"),
    SHOSHO("shosho", "処暑", "しょしょ", 150, "暑さがおさまり始めるころとされる"),
    HAKURO("hakuro", "白露", "はくろ", 165, "草花に朝露がつき始めるころとされる"),
    SHUUBUN("shuubun", "秋分", "しゅうぶん", 180, "昼と夜の長さがほぼ同じになる日。秋の彼岸の中日"),
    KANRO("kanro", "寒露", "かんろ", 195, "草木に冷たい露が降りるころとされる"),
    SOUKOU("soukou", "霜降", "そうこう", 210, "朝晩に霜が降り始めるころとされる"),
    RITTOU("rittou", "立冬", "りっとう", 225, "暦の上で冬が始まる日"),
    SHOUSETSU("shousetsu", "小雪", "しょうせつ", 240, "わずかに雪が降り始めるころとされる"),
    TAISETSU("taisetsu", "大雪", "たいせつ", 255, "雪が本格的に降り始めるころとされる"),
    TOUJI("touji", "冬至", "とうじ", 270, "一年で昼がいちばん短い日。柚子湯に入り、かぼちゃを食べる習わしがある"),
    ;

    /** The 12 terms on a multiple of 30°: a lunar month is named by the one it holds (F-5). */
    val isChuki: Boolean get() = longitude % 30 == 0
}

object SolarTerms {
    private val years = ConcurrentHashMap<Int, List<Pair<SolarTerm, Instant>>>()

    /** The 24 terms of [year] at their moments, 小寒 in January to 冬至 in December. */
    fun of(year: Int): List<Pair<SolarTerm, Instant>> = years.getOrPut(year) {
        SolarTerm.entries.zip(Astro.sunSteps(285.0, 15.0, 24, Astro.startOf(year)))
    }

    fun dateOf(term: SolarTerm, year: Int): LocalDate = Astro.dateOf(of(year).first { it.first == term }.second)
}
```

- [ ] **Step 7: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: PASS。日付が食い違ったら、その年・その節気を ledger に書いて Task 2 の上書きの表で直すかを決める（計算を合わせるために許容の分を広げない）

- [ ] **Step 8: コミット**

```bash
git add settings.gradle.kts gradle/libs.versions.toml kit-calendar
git commit -m "feat(kit-calendar): 二十四節気の時刻を Astronomy Engine で出す（REQ-KOYOMI F-1・F-5）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: 暦要項 2016〜2027 年と照らす（AC-1）

**Files:**
- Create: `kit-calendar/tools/rekiyou_fixtures.py`
- Create: `kit-calendar/src/test/resources/rekiyou/sekki.tsv`・`phases.tsv`・`holidays.tsv`（道具で作る）・`SOURCES.md`
- Test: `<test>/RekiyouTest.kt`

**Interfaces:**
- Consumes: `SolarTerms.of`、`Astro.moonQuarters`・`Astro.startOf`・`Astro.JST`（Task 1）
- Produces: テストの表（`holidays.tsv` は Task 5 が使う。1 行 `yyyy-MM-dd\t名前`）

- [ ] **Step 1: 資料を取る**（ソースの外の作業用のフォルダ。ここでは scratchpad の `rekiyou/`）

```bash
R="<scratchpad>/rekiyou"; mkdir -p "$R" && cd "$R"
for y in 2016 2017 2018 2019 2020 2021 2022 2023 2024; do curl -s --max-time 120 -o yoko$y.pdf https://eco.mtk.nao.ac.jp/koyomi/yoko/pdf/yoko$y.pdf; done
for y in 2025 2026 2027; do yy=${y:2:2}; curl -s -o s$y.html https://eco.mtk.nao.ac.jp/koyomi/yoko/$y/rekiyou${yy}2.html; curl -s -o p$y.html https://eco.mtk.nao.ac.jp/koyomi/yoko/$y/rekiyou${yy}3.html; done
# 内閣府のサイトは、この PC の Git Bash の curl では証明書を確かめられない。Windows の curl を使う
/c/Windows/System32/curl.exe -s -o syukujitsu.csv https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv
ls -la
```

- [ ] **Step 2: 表を作る道具を書く**

`kit-calendar/tools/rekiyou_fixtures.py`:

```python
"""Makes kit-calendar's test tables from the National Astronomical Observatory's 暦要項 and the
Cabinet Office's list of national holidays (platform REQ-KOYOMI D-2, AC-1).

    python -I kit-calendar/tools/rekiyou_fixtures.py <folder>

<folder> holds yoko2016.pdf … yoko2024.pdf, s2025.html … s2027.html (二十四節気・雑節), p2025.html …
p2027.html (朔弦望) and syukujitsu.csv. Writes src/test/resources/rekiyou/:

- sekki.tsv, phases.tsv: every "month day hour minute" group on that year's 二十四節気 page and
  朔弦望 page, one per line (year, month, day, hour, minute). The PDFs' Japanese text doesn't come
  out of pdftotext, so the groups are kept without their names; a test looks a computed moment up
  among them.
- holidays.tsv: the holidays of 2016-2027 (date, name), in UTF-8.
"""

import html
import re
import subprocess
import sys
from pathlib import Path

src = Path(sys.argv[1])
out = Path(__file__).resolve().parent.parent / "src" / "test" / "resources" / "rekiyou"


def groups(text):
    nums = [int(x) for x in re.findall(r"\d+", text)]
    found = set()
    for i in range(len(nums) - 3):
        m, d, h, mi = nums[i:i + 4]
        if 1 <= m <= 12 and 1 <= d <= 31 and 0 <= h <= 23 and 0 <= mi <= 59:
            found.add((m, d, h, mi))
    return sorted(found)


def pdf_pages(path):
    text = subprocess.run(
        ["pdftotext", "-layout", "-enc", "UTF-8", str(path), "-"],
        capture_output=True, encoding="utf-8", check=True,
    ).stdout
    return text.split("\f")


def html_text(path):
    raw = path.read_bytes().decode("shift_jis", errors="replace")
    return html.unescape(re.sub(r"<[^>]+>", " ", raw))


sekki, phases = [], []
for y in range(2016, 2025):
    pages = pdf_pages(src / f"yoko{y}.pdf")
    # The 二十四節気 page has the longitudes 285, 300, 315, 330 and 345; 朔弦望 is the page after it.
    i = next(k for k, p in enumerate(pages) if all(re.search(rf"\b{n}\b", p) for n in (285, 300, 315, 330, 345)))
    sekki += [(y, *g) for g in groups(pages[i])]
    phases += [(y, *g) for g in groups(pages[i + 1])]
for y in range(2025, 2028):
    sekki += [(y, *g) for g in groups(html_text(src / f"s{y}.html"))]
    phases += [(y, *g) for g in groups(html_text(src / f"p{y}.html"))]

out.mkdir(parents=True, exist_ok=True)
for name, rows in (("sekki.tsv", sekki), ("phases.tsv", phases)):
    (out / name).write_text("".join("\t".join(map(str, r)) + "\n" for r in rows), encoding="utf-8", newline="\n")

lines = []
for line in (src / "syukujitsu.csv").read_bytes().decode("shift_jis").splitlines()[1:]:
    date, name = line.split(",", 1)
    y, m, d = map(int, date.split("/"))
    if 2016 <= y <= 2027:
        lines.append(f"{y:04d}-{m:02d}-{d:02d}\t{name.strip()}\n")
(out / "holidays.tsv").write_text("".join(lines), encoding="utf-8", newline="\n")
print(f"sekki {len(sekki)}, phases {len(phases)}, holidays {len(lines)}")
```

- [ ] **Step 3: 道具を流す**

Run: `python -I kit-calendar/tools/rekiyou_fixtures.py "<scratchpad>/rekiyou"`
Expected: `sekki N, phases N, holidays N`。holidays は 2016〜2027 年の行数で、各年 16〜22 行になる。`StopIteration` が出たら、その年の PDF で二十四節気のページが見つからない。`pdftotext -layout -enc UTF-8 yoko<年>.pdf -` の出力で、そのページに 285 などの数字が分かれて出ているかを見て、見つけ方を直す

`kit-calendar/src/test/resources/rekiyou/SOURCES.md`:

```markdown
# テストの表の出どころ

`kit-calendar/tools/rekiyou_fixtures.py` で作った（2026-11-15）。

- sekki.tsv・phases.tsv: 国立天文台 暦計算室「暦要項」の二十四節気および雑節・朔弦望。2016〜2024 年は PDF（https://eco.mtk.nao.ac.jp/koyomi/yoko/pdf/yoko{年}.pdf）、2025〜2027 年は HTML（https://eco.mtk.nao.ac.jp/koyomi/yoko/{年}/rekiyou{下 2 桁}2.html・…3.html）。名前は残さず、「月 日 時 分」の組だけを残す
- holidays.tsv: 内閣府「国民の祝日について」の祝日の一覧（https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv）から 2016〜2027 年
```

- [ ] **Step 4: 失敗するテストを書く**

`<test>/RekiyouTest.kt`:

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** Every moment computed for 2016–2027 is among 暦要項's, on the same date (REQ-KOYOMI AC-1, D-2). */
class RekiyouTest {
    private fun table(name: String): Map<Int, List<LocalDateTime>> =
        javaClass.getResourceAsStream("/rekiyou/$name")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val v = line.split('\t').map(String::toInt)
                runCatching { LocalDateTime.of(v[0], v[1], v[2], v[3], v[4]) }.getOrNull()
            }
            .groupBy { it.year }

    private fun assertIn(what: String, at: Instant, theirs: List<LocalDateTime>) {
        val ours = at.atOffset(Astro.JST).toLocalDateTime()
        assertTrue(
            "$what at $ours is not in 暦要項 (same date, within 2 minutes)",
            theirs.any { it.toLocalDate() == ours.toLocalDate() && Duration.between(it, ours).abs().toMinutes() <= 2 },
        )
    }

    @Test
    fun `the 24 terms of 2016-2027`() {
        val theirs = table("sekki.tsv")
        for (y in 2016..2027) SolarTerms.of(y).forEach { (term, at) -> assertIn("${term.label} $y", at, theirs.getValue(y)) }
    }

    @Test
    fun `the moon's quarters of 2016-2027`() {
        val theirs = table("phases.tsv")
        for (y in 2016..2027) {
            val quarters = Astro.moonQuarters(Astro.startOf(y), Astro.startOf(y + 1))
            assertTrue("$y: ${quarters.size} quarters", quarters.size in 48..51)
            quarters.forEach { (q, at) -> assertIn("quarter $q of $y", at, theirs.getValue(y)) }
        }
    }
}
```

- [ ] **Step 5: 流して確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: PASS（Task 1 で計算はできているので、ここで初めて失敗するのは、表の取り出しの誤りか、計算と暦要項の食い違い）。失敗したら、その時刻を PDF のページの画像（Read ツールで `pages` を指定）で確かめる。
- PDF の取り出しの誤りなら、`rekiyou_fixtures.py` を直して流し直す
- 計算と暦要項が本当に食い違うなら、`<pkg>/Overrides.kt` に `internal val SEKKI_DATES: Map<Pair<SolarTerm, Int>, LocalDate>` を作って `SolarTerms.dateOf` がそれを先に見るようにし（D-2）、ledger に Ruling を書く

- [ ] **Step 6: コミット**

```bash
git add kit-calendar/tools kit-calendar/src/test
git commit -m "test(kit-calendar): 2016〜2027 年の暦要項と、節気と月の満ち欠けを照らす（REQ-KOYOMI AC-1）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: 旧暦・六曜・十五夜（F-4 ③⑥、F-5）

**Files:**
- Create: `<pkg>/Lunisolar.kt`、`<pkg>/Rokuyou.kt`
- Test: `<test>/LunisolarTest.kt`

**Interfaces:**
- Consumes: `SolarTerms.of`・`SolarTerm.isChuki`・`SolarTerm.longitude`、`Astro`（Task 1）
- Produces: `data class LunarDate(val month: Int, val day: Int, val leap: Boolean)`、`object Lunisolar { fun of(date: LocalDate): LunarDate; fun dateOf(year: Int, month: Int, day: Int): LocalDate? }`、`enum class Rokuyou(key, label, reading, note) { …; companion object { fun of(date: LocalDate): Rokuyou } }`、`internal object Kanshi { fun index(date: LocalDate): Int; fun stem(date: LocalDate): Int; fun branch(date: LocalDate): Int }`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LunisolarTest {
    @Test
    fun `十五夜 of 2016-2027 is the 15th of the 8th month (国立天文台の暦要項の朔と秋分から)`() {
        val meigetsu = listOf("2016-09-15", "2017-10-04", "2018-09-24", "2019-09-13", "2020-10-01", "2021-09-21",
            "2022-09-10", "2023-09-29", "2024-09-17", "2025-10-06", "2026-09-25", "2027-09-15").map(LocalDate::parse)
        for (d in meigetsu) {
            assertEquals("$d", LunarDate(8, 15, false), Lunisolar.of(d))
            assertEquals(d, Lunisolar.dateOf(d.year, 8, 15))
        }
    }

    @Test
    fun `a month without 中気 is the leap one of the month before (2025 閏6月, 2023 閏2月)`() {
        assertEquals(LunarDate(6, 8, true), Lunisolar.of(LocalDate.of(2025, 8, 1)))
        assertEquals(true, Lunisolar.of(LocalDate.of(2023, 4, 1)).leap)
        assertEquals(2, Lunisolar.of(LocalDate.of(2023, 4, 1)).month)
    }

    @Test
    fun `15 November 2026 is the 7th of the 10th month, 仏滅 (朔 9 November, 小雪 22 November)`() {
        assertEquals(LunarDate(10, 7, false), Lunisolar.of(LocalDate.of(2026, 11, 15)))
        assertEquals(Rokuyou.BUTSUMETSU, Rokuyou.of(LocalDate.of(2026, 11, 15)))
        // The 1st of a month is 先勝 in the 1st and 7th months, and so on in turn.
        assertEquals(Rokuyou.SENSHOU, Rokuyou.of(Lunisolar.dateOf(2026, 1, 1)!!))
        assertEquals(Rokuyou.TOMOBIKI, Rokuyou.of(Lunisolar.dateOf(2026, 2, 1)!!))
    }

    @Test
    fun `the sexagenary day: 1 January 2024 was 甲子, 1 January 2000 戊午`() {
        assertEquals(0, Kanshi.index(LocalDate.of(2024, 1, 1)))
        assertEquals(54, Kanshi.index(LocalDate.of(2000, 1, 1)))
        assertEquals(4, Kanshi.stem(LocalDate.of(2000, 1, 1)))
        assertEquals(6, Kanshi.branch(LocalDate.of(2000, 1, 1)))
    }
}
```

（2025-08-01: 閏 6 月は 2025-07-25 の朔から。8 月 1 日はその 8 日目。日付が 1 日ずれて失敗したら、その年の朔の日付を phases.tsv で確かめてから期待値を直す。期待値を直したときは ledger に書く）

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: コンパイルエラー（`Lunisolar`・`Rokuyou`・`Kanshi` がない）

- [ ] **Step 3: `Lunisolar.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** A day of the old lunisolar calendar: its month (1–12), day (1–30), and whether the month is a leap one. */
data class LunarDate(val month: Int, val day: Int, val leap: Boolean)

/**
 * The old lunisolar calendar (REQ-KOYOMI F-5): a month starts on the day (Japan's) of a new moon and
 * is named by the 中気 it holds (雨水 the 1st, 春分 the 2nd, … 大寒 the 12th); a month without one is
 * the leap month of the one before. Not for the year 2033 and after, whose months this rule can't name
 * (the "2033 problem"); see REQ-KOYOMI R-6.
 */
object Lunisolar {
    private val CHUKI_MONTH = mapOf(330 to 1, 0 to 2, 30 to 3, 60 to 4, 90 to 5, 120 to 6, 150 to 7, 180 to 8, 210 to 9, 240 to 10, 270 to 11, 300 to 12)
    private val newMoons = ConcurrentHashMap<Int, List<LocalDate>>()
    private val byLunar = ConcurrentHashMap<Int, Map<LunarDate, LocalDate>>()

    fun of(date: LocalDate): LunarDate {
        val starts = (date.year - 1..date.year + 1).flatMap(::newMoonsOf)
        val k = starts.indexOfLast { !it.isAfter(date) }
        val (month, leap) = monthOf(starts, k)
        return LunarDate(month, ChronoUnit.DAYS.between(starts[k], date).toInt() + 1, leap)
    }

    /** The day of [year] (Gregorian) that is the [day] of the [month] (not a leap one), or null. */
    fun dateOf(year: Int, month: Int, day: Int): LocalDate? = byLunar.getOrPut(year) {
        generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == year }
            .associateBy(::of).filterKeys { !it.leap }
    }[LunarDate(month, day, false)]

    private fun newMoonsOf(year: Int): List<LocalDate> = newMoons.getOrPut(year) {
        Astro.moonQuarters(Astro.startOf(year), Astro.startOf(year + 1)).filter { it.first == 0 }.map { Astro.dateOf(it.second) }
    }

    private fun monthOf(starts: List<LocalDate>, k: Int): Pair<Int, Boolean> {
        chukiIn(starts[k], starts[k + 1])?.let { return it to false }
        val before = chukiIn(starts[k - 1], starts[k]) ?: error("Two months without 中気 in a row from ${starts[k - 1]}")
        return before to true
    }

    /** The month named by the first 中気 whose day falls in [from, until). */
    private fun chukiIn(from: LocalDate, until: LocalDate): Int? =
        (from.year..until.year).flatMap(SolarTerms::of)
            .filter { it.first.isChuki }
            .firstOrNull { val d = Astro.dateOf(it.second); !d.isBefore(from) && d.isBefore(until) }
            ?.let { CHUKI_MONTH.getValue(it.first.longitude) }
}

/** The sexagenary count of days (干支). 0 is 甲子; [stem] 0 is 甲 (4 戊), [branch] 0 is 子 (11 亥). */
internal object Kanshi {
    /** The Julian day number of [date] (at its noon). */
    private fun jdn(date: LocalDate): Long = date.toEpochDay() + 2440588

    fun index(date: LocalDate): Int = Math.floorMod(jdn(date) - 11, 60L).toInt()
    fun stem(date: LocalDate): Int = index(date) % 10
    fun branch(date: LocalDate): Int = index(date) % 12
}
```

`docs/requirements/REQ-KOYOMI.md` の「未決事項・リスク」に足す（実装と同じコミットに入れる）:

```markdown
| R-6 | 旧暦の月の決め方（中気を含む月の名をとる）では、2033 年の秋から 2034 年の春の月が決まらない（2033 年問題） | `Lunisolar` は 2033 年より前の日付で使う。2033 年までに、暦の団体などが示した決め方を上書きの表で入れる |
```

- [ ] **Step 4: `Rokuyou.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate

/**
 * The six days of the old calendar's notes (六曜), in the turn of (month + day) mod 6 (REQ-KOYOMI D-4).
 * The notes say what was said of each, as said; none of them says to do or avoid anything.
 */
enum class Rokuyou(val key: String, val label: String, val reading: String, val note: String) {
    TAIAN("taian", "大安", "たいあん", "六曜でいちばん吉とされてきた日"),
    SHAKKOU("shakkou", "赤口", "しゃっこう", "正午ごろだけが吉で、ほかは凶とされてきた日"),
    SENSHOU("senshou", "先勝", "せんしょう", "「先んずれば勝つ」とされ、午前が吉と言われてきた日"),
    TOMOBIKI("tomobiki", "友引", "ともびき", "もとは「共引」で、勝負がつかない日とされてきた"),
    SENBU("senbu", "先負", "せんぶ", "「先んずれば負ける」とされ、午後が吉と言われてきた日"),
    BUTSUMETSU("butsumetsu", "仏滅", "ぶつめつ", "六曜でいちばん凶とされてきた日。名前に「仏」とあるが、仏教とは関係がない"),
    ;

    companion object {
        fun of(date: LocalDate): Rokuyou = Lunisolar.of(date).let { entries[(it.month + it.day) % 6] }
    }
}
```

- [ ] **Step 5: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: PASS

- [ ] **Step 6: コミット**

```bash
git add kit-calendar docs/requirements/REQ-KOYOMI.md
git commit -m "feat(kit-calendar): 旧暦・六曜・干支を出す（REQ-KOYOMI F-4・F-5）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: 雑節（F-4 ③、「暦の種類」zassetsu）

**Files:**
- Create: `<pkg>/Event.kt`、`<pkg>/Zassetsu.kt`
- Test: `<test>/ZassetsuTest.kt`

**Interfaces:**
- Consumes: `SolarTerms.dateOf`、`Astro.sunAt`（Task 1）、`Kanshi.stem`（Task 3）
- Produces: `enum class KoyomiKind(val json: String, val certainty: Certainty)`、`enum class Certainty { FIXED, TRADITION, DESIGNATED, REGIONAL }`、`internal data class Event(kind, key, name, start, end = start, note, reading = null, designatedBy = null, regions = null) { val id: String }`、`internal object Zassetsu { fun of(year: Int): List<Event> }`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ZassetsuTest {
    private fun starts(year: Int) = Zassetsu.of(year).associate { it.key to it.start }

    /** 暦要項 2025–2027, 雑節 (the days given; 土用 is when it begins). */
    @Test
    fun `the seasonal days of 2025-2027 are 暦要項's (AC-1)`() {
        val rekiyou = mapOf(
            2025 to listOf("doyou-winter" to "01-17", "setsubun" to "02-02", "higan-spring" to "03-17", "doyou-spring" to "04-17", "hachijuhachiya" to "05-01",
                "nyubai" to "06-11", "hangesho" to "07-01", "doyou-summer" to "07-19", "nihyakutoka" to "08-31", "higan-autumn" to "09-20", "doyou-autumn" to "10-20"),
            2026 to listOf("doyou-winter" to "01-17", "setsubun" to "02-03", "higan-spring" to "03-17", "doyou-spring" to "04-17", "hachijuhachiya" to "05-02",
                "nyubai" to "06-11", "hangesho" to "07-02", "doyou-summer" to "07-20", "nihyakutoka" to "09-01", "higan-autumn" to "09-20", "doyou-autumn" to "10-20"),
            2027 to listOf("doyou-winter" to "01-17", "setsubun" to "02-03", "higan-spring" to "03-18", "doyou-spring" to "04-17", "hachijuhachiya" to "05-02",
                "nyubai" to "06-11", "hangesho" to "07-02", "doyou-summer" to "07-20", "nihyakutoka" to "09-01", "higan-autumn" to "09-20", "doyou-autumn" to "10-21"),
        )
        for ((y, days) in rekiyou) {
            val ours = starts(y)
            for ((key, md) in days) assertEquals("$key $y", LocalDate.parse("$y-$md"), ours.getValue(key))
        }
    }

    @Test
    fun `彼岸 is the 7 days around the equinox; 土用 ends the day before the next 立`() {
        val e = Zassetsu.of(2026).associateBy { it.key }
        assertEquals(LocalDate.of(2026, 3, 23), e.getValue("higan-spring").end)
        assertEquals(LocalDate.of(2026, 8, 6), e.getValue("doyou-summer").end)
        assertEquals(LocalDate.of(2026, 2, 3), e.getValue("doyou-winter").end)
    }

    @Test
    fun `社日 is the 戊 day nearest the equinox; 二百二十日 ten days after 二百十日`() {
        for (y in 2016..2027) {
            val e = Zassetsu.of(y).associateBy { it.key }
            for ((key, term) in listOf("shanichi-spring" to SolarTerm.SHUNBUN, "shanichi-autumn" to SolarTerm.SHUUBUN)) {
                val day = e.getValue(key).start
                assertEquals("$key $y", 4, Kanshi.stem(day))
                assertTrue("$key $y", Math.abs(ChronoUnit.DAYS.between(SolarTerms.dateOf(term, y), day)) <= 5)
            }
            assertEquals(e.getValue("nihyakutoka").start.plusDays(10), e.getValue("nihyakuhatsuka").start)
        }
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: コンパイルエラー（`Zassetsu` がない）

- [ ] **Step 3: `Event.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate

/** How sure a kind of fact is, which sets how it may be told (REQ-KOYOMI D-7, F-17). */
enum class Certainty {
    /** By astronomy or by law: told as a fact. */
    FIXED,

    /** Said of old: told as what has been said. */
    TRADITION,

    /** Set by someone: told with who set it. */
    DESIGNATED,

    /** Kept in some places: told with where. */
    REGIONAL,
}

/** The kinds of the calendar's facts, in `channel.json` spelling (REQ-KOYOMI「暦の種類」). */
enum class KoyomiKind(val json: String, val certainty: Certainty) {
    SEKKI("sekki", Certainty.FIXED),
    KOU("kou", Certainty.TRADITION),
    ZASSETSU("zassetsu", Certainty.TRADITION),
    SEKKU("sekku", Certainty.TRADITION),
    LUNAR("lunar", Certainty.TRADITION),
    HOLIDAY("holiday", Certainty.FIXED),
    MOON("moon", Certainty.FIXED),
    ROKUYOU("rokuyou", Certainty.TRADITION),
    KINENBI("kinenbi", Certainty.DESIGNATED),
    REGIONAL("regional", Certainty.REGIONAL),
    ;

    companion object {
        fun of(json: String): KoyomiKind? = entries.firstOrNull { it.json == json }
    }
}

/** A day, or days from [start] to [end], the calendar has something to say of. */
internal data class Event(
    val kind: KoyomiKind,
    val key: String,
    val name: String,
    val start: LocalDate,
    val end: LocalDate = start,
    val note: String,
    val reading: String? = null,
    val designatedBy: String? = null,
    val regions: String? = null,
) {
    /** Kind, key and first day: one per occurrence (REQ-KOYOMI F-2). */
    val id: String get() = "${kind.json}:$key:$start"
}
```

- [ ] **Step 4: `Zassetsu.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** The seasonal days (雑節) of a year, from the solar terms and the Sun's longitude (REQ-KOYOMI「暦の種類」). */
internal object Zassetsu {
    private val years = ConcurrentHashMap<Int, List<Event>>()

    fun of(year: Int): List<Event> = years.getOrPut(year) {
        fun term(t: SolarTerm) = SolarTerms.dateOf(t, year)
        fun sun(lon: Double) = Astro.dateOf(Astro.sunAt(lon, year))
        fun day(key: String, name: String, reading: String, on: LocalDate, note: String, end: LocalDate = on) =
            Event(KoyomiKind.ZASSETSU, key, name, on, end, note, reading)
        val risshun = term(SolarTerm.RISSHUN)
        val shunbun = term(SolarTerm.SHUNBUN)
        val shuubun = term(SolarTerm.SHUUBUN)
        val doyou = "立春・立夏・立秋・立冬の前のおよそ 18 日間。季節の変わり目とされる"
        listOf(
            day("doyou-winter", "冬の土用", "ふゆのどよう", sun(297.0), doyou, risshun.minusDays(1)),
            day("setsubun", "節分", "せつぶん", risshun.minusDays(1), "立春の前の日。豆をまいて邪気を払う習わしがある"),
            day("higan-spring", "春の彼岸", "はるのひがん", shunbun.minusDays(3), "春分の日を中日とする 7 日間。墓参りをする習わしがある", shunbun.plusDays(3)),
            day("shanichi-spring", "社日", "しゃにち", nearestTsuchinoe(shunbun), "春分・秋分にいちばん近い戊（つちのえ）の日。土地の神をまつる日とされる"),
            day("doyou-spring", "春の土用", "はるのどよう", sun(27.0), doyou, term(SolarTerm.RIKKA).minusDays(1)),
            day("hachijuhachiya", "八十八夜", "はちじゅうはちや", risshun.plusDays(87), "立春から数えて 88 日目。茶摘みや種まきの目安とされてきた"),
            day("nyubai", "入梅", "にゅうばい", sun(80.0), "暦の上で梅雨に入る日"),
            day("hangesho", "半夏生", "はんげしょう", sun(100.0), "田植えを終える目安とされてきた日"),
            day("doyou-summer", "夏の土用", "なつのどよう", sun(117.0), "$doyou。夏の土用の丑の日にうなぎを食べる習わしがある", term(SolarTerm.RISSHUU).minusDays(1)),
            day("nihyakutoka", "二百十日", "にひゃくとおか", risshun.plusDays(209), "立春から数えて 210 日目。台風の来やすい日として、農家が気をつけてきた"),
            day("nihyakuhatsuka", "二百二十日", "にひゃくはつか", risshun.plusDays(219), "立春から数えて 220 日目。二百十日と並んで、台風に気をつける日とされてきた"),
            day("higan-autumn", "秋の彼岸", "あきのひがん", shuubun.minusDays(3), "秋分の日を中日とする 7 日間。墓参りをする習わしがある", shuubun.plusDays(3)),
            day("shanichi-autumn", "社日", "しゃにち", nearestTsuchinoe(shuubun), "春分・秋分にいちばん近い戊（つちのえ）の日。土地の神をまつる日とされる"),
            day("doyou-autumn", "秋の土用", "あきのどよう", sun(207.0), doyou, term(SolarTerm.RITTOU).minusDays(1)),
        )
    }

    /** The 戊 day nearest [day]; of two as near, the one before (REQ-KOYOMI「暦の種類」zassetsu). */
    private fun nearestTsuchinoe(day: LocalDate): LocalDate =
        (-5L..5L).map { day.plusDays(it) }.filter { Kanshi.stem(it) == 4 }
            .minWith(compareBy<LocalDate> { Math.abs(ChronoUnit.DAYS.between(day, it)) }.thenBy { it })
}
```

- [ ] **Step 5: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: PASS

- [ ] **Step 6: コミット**

```bash
git add kit-calendar
git commit -m "feat(kit-calendar): 雑節を出す（REQ-KOYOMI 暦の種類 zassetsu）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: 国民の祝日（F-4 ③、「暦の種類」holiday）

**Files:**
- Create: `<pkg>/Holidays.kt`
- Test: `<test>/HolidaysTest.kt`

**Interfaces:**
- Consumes: `SolarTerms.dateOf`（Task 1）、`Event`・`KoyomiKind`（Task 4）、`rekiyou/holidays.tsv`（Task 2）
- Produces: `internal object Holidays { fun of(year: Int): List<Event> }`（2016 年以降。それより前は `require` で落とす）

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {
    private val cabinetOffice: Map<LocalDate, String> =
        javaClass.getResourceAsStream("/rekiyou/holidays.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.associate { line -> line.split('\t').let { LocalDate.parse(it[0]) to it[1] } }

    @Test
    fun `the holidays of 2016-2027 are the Cabinet Office's (AC-1)`() {
        for (y in 2016..2027) {
            val ours = Holidays.of(y).associate { it.start to it.name }
            val theirs = cabinetOffice.filterKeys { it.year == y }
            assertEquals("$y", theirs.keys.sorted(), ours.keys.sorted())
            // Names: the list calls 振替休日 and 国民の休日 both 「休日」.
            for ((day, name) in theirs) {
                val mine = ours.getValue(day)
                if (name.startsWith("休日")) assertTrue("$day $mine", mine in setOf("振替休日", "国民の休日", "天皇の即位の日", "即位礼正殿の儀の行われる日"))
                else assertEquals("$day", name.substringBefore('（'), mine)
            }
        }
    }

    @Test
    fun `every holiday says what it is for`() {
        Holidays.of(2026).forEach { assertTrue(it.name, it.note.isNotBlank()) }
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: コンパイルエラー（`Holidays` がない）

- [ ] **Step 3: `Holidays.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/**
 * The national holidays of a year by the law (国民の祝日に関する法律) as it has stood since 2016, with
 * the years it was set aside (2019's enthronement, 2020's and 2021's moves for the Olympics). Each
 * note is the law's own words of what the day is for (第 2 条), or what makes the day off (第 3 条).
 */
internal object Holidays {
    private class Law(val name: String, val reading: String, val note: String)

    private val LAW = mapOf(
        "ganjitsu" to Law("元日", "がんじつ", "年のはじめを祝う。"),
        "seijin" to Law("成人の日", "せいじんのひ", "おとなになつたことを自覚し、みずから生き抜こうとする青年を祝いはげます。"),
        "kenkoku" to Law("建国記念の日", "けんこくきねんのひ", "建国をしのび、国を愛する心を養う。"),
        "tennou" to Law("天皇誕生日", "てんのうたんじょうび", "天皇の誕生日を祝う。"),
        "shunbun" to Law("春分の日", "しゅんぶんのひ", "自然をたたえ、生物をいつくしむ。"),
        "showa" to Law("昭和の日", "しょうわのひ", "激動の日々を経て、復興を遂げた昭和の時代を顧み、国の将来に思いをいたす。"),
        "kenpou" to Law("憲法記念日", "けんぽうきねんび", "日本国憲法の施行を記念し、国の成長を期する。"),
        "midori" to Law("みどりの日", "みどりのひ", "自然に親しむとともにその恩恵に感謝し、豊かな心をはぐくむ。"),
        "kodomo" to Law("こどもの日", "こどものひ", "こどもの人格を重んじ、こどもの幸福をはかるとともに、母に感謝する。"),
        "umi" to Law("海の日", "うみのひ", "海の恩恵に感謝するとともに、海洋国日本の繁栄を願う。"),
        "yama" to Law("山の日", "やまのひ", "山に親しむ機会を得て、山の恩恵に感謝する。"),
        "keirou" to Law("敬老の日", "けいろうのひ", "多年にわたり社会につくしてきた老人を敬愛し、長寿を祝う。"),
        "shuubun" to Law("秋分の日", "しゅうぶんのひ", "祖先をうやまい、なくなつた人々をしのぶ。"),
        "taiiku" to Law("体育の日", "たいいくのひ", "スポーツにしたしみ、健康な心身をつちかう。"),
        "sports" to Law("スポーツの日", "スポーツのひ", "スポーツを楽しみ、他者を尊重する精神を培うとともに、健康で活力ある社会の実現を願う。"),
        "bunka" to Law("文化の日", "ぶんかのひ", "自由と平和を愛し、文化をすすめる。"),
        "kinrou" to Law("勤労感謝の日", "きんろうかんしゃのひ", "勤労をたつとび、生産を祝い、国民たがいに感謝しあう。"),
        "sokui" to Law("天皇の即位の日", "てんのうのそくいのひ", "天皇の即位の日を休日とする法律（平成 30 年法律第 99 号）による休日。"),
        "sokuirei" to Law("即位礼正殿の儀の行われる日", "そくいれいせいでんのぎのおこなわれるひ", "即位礼正殿の儀の行われる日を休日とする法律（平成 30 年法律第 99 号）による休日。"),
        "furikae" to Law("振替休日", "ふりかえきゅうじつ", "国民の祝日が日曜日に当たったときの、その後のいちばん近い祝日でない日（祝日法第 3 条第 2 項）。"),
        "kokumin" to Law("国民の休日", "こくみんのきゅうじつ", "前の日と後の日が国民の祝日である日（祝日法第 3 条第 3 項）。"),
    )

    /** The days moved for the Olympics: 海の日, スポーツの日 and 山の日 (東京オリンピック特措法). */
    private val MOVED = mapOf(
        2020 to mapOf("umi" to LocalDate.of(2020, 7, 23), "sports" to LocalDate.of(2020, 7, 24), "yama" to LocalDate.of(2020, 8, 10)),
        2021 to mapOf("umi" to LocalDate.of(2021, 7, 22), "sports" to LocalDate.of(2021, 7, 23), "yama" to LocalDate.of(2021, 8, 8)),
    )

    private val years = ConcurrentHashMap<Int, List<Event>>()

    fun of(year: Int): List<Event> = years.getOrPut(year) {
        require(year >= 2016) { "Holidays are kept from 2016 on: $year" }
        val moved = MOVED[year].orEmpty()
        fun monday(month: Int, n: Int) = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))
        val days = sortedMapOf<LocalDate, String>()
        fun add(key: String, day: LocalDate) { days[moved[key] ?: day] = key }
        add("ganjitsu", LocalDate.of(year, 1, 1))
        add("seijin", monday(1, 2))
        add("kenkoku", LocalDate.of(year, 2, 11))
        if (year <= 2018) add("tennou", LocalDate.of(year, 12, 23))
        if (year >= 2020) add("tennou", LocalDate.of(year, 2, 23))
        add("shunbun", SolarTerms.dateOf(SolarTerm.SHUNBUN, year))
        add("showa", LocalDate.of(year, 4, 29))
        add("kenpou", LocalDate.of(year, 5, 3))
        add("midori", LocalDate.of(year, 5, 4))
        add("kodomo", LocalDate.of(year, 5, 5))
        add("umi", monday(7, 3))
        add("yama", LocalDate.of(year, 8, 11))
        add("keirou", monday(9, 3))
        add("shuubun", SolarTerms.dateOf(SolarTerm.SHUUBUN, year))
        add(if (year >= 2020) "sports" else "taiiku", monday(10, 2))
        add("bunka", LocalDate.of(year, 11, 3))
        add("kinrou", LocalDate.of(year, 11, 23))
        if (year == 2019) {
            add("sokui", LocalDate.of(2019, 5, 1))
            add("sokuirei", LocalDate.of(2019, 10, 22))
        }
        // 振替休日: a holiday on a Sunday makes the next day that isn't one a day off (第 3 条第 2 項).
        for (day in days.keys.toList().filter { it.dayOfWeek == DayOfWeek.SUNDAY }) {
            var next = day.plusDays(1)
            while (next in days) next = next.plusDays(1)
            days[next] = "furikae"
        }
        // 国民の休日: a day between two national holidays, not one itself (第 3 条第 3 項). 振替休日 isn't one.
        for (day in days.keys.toList()) {
            val between = day.plusDays(1)
            val after = days[between.plusDays(1)]
            if (between !in days && after != null && after != "furikae" && days[day] != "furikae") {
                days[between] = "kokumin"
            }
        }
        days.map { (day, key) -> LAW.getValue(key).let { Event(KoyomiKind.HOLIDAY, key, it.name, day, note = it.note, reading = it.reading) } }
    }
}
```

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: PASS。国民の休日・振替休日の日がずれたら、内閣府の一覧のその年を見て決まりを直す（2019 年 4 月 30 日・5 月 2 日は国民の休日、5 月 6 日は振替休日）

- [ ] **Step 5: コミット**

```bash
git add kit-calendar
git commit -m "feat(kit-calendar): 国民の祝日を出す（REQ-KOYOMI 暦の種類 holiday）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: 七十二候・五節句と年中行事・記念日・地域ごとの行事の表（F-6・F-7、AC-3）

**Files:**
- Create: `<pkg>/Tables.kt`
- Test: `<test>/TablesTest.kt`

**Interfaces:**
- Consumes: `Lunisolar.dateOf`・`Kanshi.branch`（Task 3）、`Event`・`KoyomiKind`（Task 4）
- Produces: `internal object Tables { val KOU: List<Kou>; fun sekku(year: Int): List<Event>; fun lunar(year: Int): List<Event>; fun kinenbi(year: Int): List<Event>; fun regional(year: Int): List<Event>; val KINENBI: List<Kinenbi>; val REGIONAL: List<Regional> }`、`internal data class Kou(val name: String, val reading: String, val note: String)`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TablesTest {
    @Test
    fun `72 pentads, 立春 first, each with its reading and meaning`() {
        assertEquals(72, Tables.KOU.size)
        assertEquals("東風解凍", Tables.KOU[0].name)
        assertEquals("山茶始開", Tables.KOU[54].name)
        assertEquals("地始凍", Tables.KOU[55].name)
        assertEquals("鶏始乳", Tables.KOU[71].name)
        Tables.KOU.forEach { assertTrue(it.name, it.reading.isNotBlank() && it.note.isNotBlank()) }
    }

    @Test
    fun `designated days: who set them and where that was checked, two a month or more, no products (AC-3, D-5)`() {
        Tables.KINENBI.forEach {
            assertTrue(it.name, it.designatedBy.isNotBlank() && it.source.isNotBlank() && it.note.isNotBlank())
        }
        for (m in 1..12) assertTrue("month $m", Tables.KINENBI.count { it.month == m } >= 2)
    }

    @Test
    fun `regional days say where and from what source (AC-3, D-6)`() {
        Tables.REGIONAL.forEach { assertTrue(it.name, it.regions.isNotBlank() && it.source.isNotBlank()) }
        val r = Tables.regional(2026).associateBy { it.key }
        assertEquals(LocalDate.of(2026, 7, 13), r.getValue("bon-july").start)
        assertEquals(LocalDate.of(2026, 8, 16), r.getValue("bon-august").end)
        assertEquals(10, Lunisolar.of(r.getValue("inoko").start).month)
        assertEquals(11, Kanshi.branch(r.getValue("inoko").start))
    }

    @Test
    fun `十五夜 and 十三夜, the festivals of the year`() {
        val lunar = Tables.lunar(2026).associate { it.key to it.start }
        assertEquals(LocalDate.of(2026, 9, 25), lunar.getValue("jugoya"))
        assertEquals(LunarDate(9, 13, false), Lunisolar.of(lunar.getValue("jusanya")))
        assertEquals(LocalDate.of(2026, 11, 15), Tables.sekku(2026).single { it.key == "shichigosan" }.start)
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: コンパイルエラー（`Tables` がない）

- [ ] **Step 3: 記念日の出どころを確かめる**

下の `KINENBI` の各行の `designatedBy`（定めた団体）と日付を、その団体の公式の頁（なければ官公庁の頁）で確かめ、その URL を `source` に書く。確かめられない行は消す（各月 2 件以上が残ること）。確かめた日を `Tables.kt` の `KINENBI` の KDoc に書く。

- [ ] **Step 4: `Tables.kt` を書く**

（`source` の `"<確かめた URL>"` は Step 3 で確かめた URL に置き換える。置き換え忘れは Step 5 のテストでは見つからないので、コミットの前に `grep -n "確かめた URL" kit-calendar` が何も出さないことを確かめる）

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate

internal data class Kou(val name: String, val reading: String, val note: String)

/** A designated day (D-5): who set it, the line on why, and where that was checked. */
internal data class Kinenbi(val month: Int, val day: Int, val key: String, val name: String, val designatedBy: String, val note: String, val source: String)

/** A day kept in some places (D-6): where, and when in [year] ([days] gives the first and last day, or null). */
internal class Regional(
    val key: String,
    val name: String,
    val reading: String,
    val regions: String,
    val note: String,
    val source: String,
    val days: (year: Int) -> Pair<LocalDate, LocalDate>?,
)

/** The calendar's tables, written by the operator (REQ-KOYOMI D-3, D-5, D-6, F-6, F-7). */
internal object Tables {
    /** The 72 pentads (七十二候, 略本暦), 立春's first one first; the k-th begins at the Sun's longitude 315 + 5k. */
    val KOU = listOf(
        Kou("東風解凍", "はるかぜこおりをとく", "春の風が、川や湖の氷を解かし始めるころ"),
        Kou("黄鶯睍睆", "うぐいすなく", "うぐいすが山里で鳴き始めるころ"),
        Kou("魚上氷", "うおこおりをいずる", "割れた氷の間から、魚が跳ね上がるころ"),
        Kou("土脉潤起", "つちのしょううるおいおこる", "雨が降って、土が湿り気を含むころ"),
        Kou("霞始靆", "かすみはじめてたなびく", "霞がたなびき始めるころ"),
        Kou("草木萌動", "そうもくめばえいずる", "草木が芽吹き始めるころ"),
        Kou("蟄虫啓戸", "すごもりむしとをひらく", "冬ごもりの虫が、戸を開いて出てくるころ"),
        Kou("桃始笑", "ももはじめてさく", "桃の花が咲き始めるころ"),
        Kou("菜虫化蝶", "なむしちょうとなる", "青虫が羽化して、紋白蝶になるころ"),
        Kou("雀始巣", "すずめはじめてすくう", "雀が巣を作り始めるころ"),
        Kou("桜始開", "さくらはじめてさく", "桜の花が咲き始めるころ"),
        Kou("雷乃発声", "かみなりすなわちこえをはっす", "遠くで春の雷が鳴り始めるころ"),
        Kou("玄鳥至", "つばめきたる", "つばめが南の国から渡ってくるころ"),
        Kou("鴻雁北", "こうがんかえる", "雁が北へ帰っていくころ"),
        Kou("虹始見", "にじはじめてあらわる", "雨上がりに、虹が見え始めるころ"),
        Kou("葭始生", "あしはじめてしょうず", "水辺の葦が芽を出し始めるころ"),
        Kou("霜止出苗", "しもやみてなえいずる", "霜が終わり、稲の苗が育つころ"),
        Kou("牡丹華", "ぼたんはなさく", "牡丹の花が咲くころ"),
        Kou("蛙始鳴", "かわずはじめてなく", "蛙が鳴き始めるころ"),
        Kou("蚯蚓出", "みみずいずる", "みみずが地上に出てくるころ"),
        Kou("竹笋生", "たけのこしょうず", "たけのこが生えてくるころ"),
        Kou("蚕起食桑", "かいこおきてくわをはむ", "蚕が桑の葉を盛んに食べるころ"),
        Kou("紅花栄", "べにばなさかう", "紅花が盛んに咲くころ"),
        Kou("麦秋至", "むぎのときいたる", "麦が熟して、麦秋を迎えるころ"),
        Kou("蟷螂生", "かまきりしょうず", "かまきりが生まれるころ"),
        Kou("腐草為蛍", "くされたるくさほたるとなる", "草むらで蛍が光り始めるころ。昔は、腐った草が蛍になると考えられていた"),
        Kou("梅子黄", "うめのみきばむ", "梅の実が黄ばんで熟すころ"),
        Kou("乃東枯", "なつかれくさかるる", "夏枯草（うつぼぐさ）が枯れるころ"),
        Kou("菖蒲華", "あやめはなさく", "あやめの花が咲くころ"),
        Kou("半夏生", "はんげしょうず", "半夏（からすびしゃく）が生えるころ"),
        Kou("温風至", "あつかぜいたる", "熱い風が吹き始めるころ"),
        Kou("蓮始開", "はすはじめてひらく", "蓮の花が開き始めるころ"),
        Kou("鷹乃学習", "たかすなわちわざをならう", "鷹の幼鳥が、飛ぶことを覚えるころ"),
        Kou("桐始結花", "きりはじめてはなをむすぶ", "桐が花を結び始めるころ"),
        Kou("土潤溽暑", "つちうるおうてむしあつし", "土が湿って、蒸し暑くなるころ"),
        Kou("大雨時行", "たいうときどきふる", "ときどき大雨が降るころ"),
        Kou("涼風至", "すずかぜいたる", "涼しい風が立ち始めるころ"),
        Kou("寒蝉鳴", "ひぐらしなく", "ひぐらしが鳴き始めるころ"),
        Kou("蒙霧升降", "ふかききりまとう", "深い霧が立ちこめるころ"),
        Kou("綿柎開", "わたのはなしべひらく", "綿を包む萼（がく）が開くころ"),
        Kou("天地始粛", "てんちはじめてさむし", "暑さがようやくおさまるころ"),
        Kou("禾乃登", "こくものすなわちみのる", "稲が実るころ"),
        Kou("草露白", "くさのつゆしろし", "草に降りた露が、白く光るころ"),
        Kou("鶺鴒鳴", "せきれいなく", "せきれいが鳴き始めるころ"),
        Kou("玄鳥去", "つばめさる", "つばめが南へ帰っていくころ"),
        Kou("雷乃収声", "かみなりすなわちこえをおさむ", "雷が鳴らなくなるころ"),
        Kou("蟄虫坏戸", "むしかくれてとをふさぐ", "虫が土の中にこもり、戸をふさぐころ"),
        Kou("水始涸", "みずはじめてかるる", "田の水を落として、稲刈りに備えるころ"),
        Kou("鴻雁来", "こうがんきたる", "雁が北から渡ってくるころ"),
        Kou("菊花開", "きくのはなひらく", "菊の花が咲くころ"),
        Kou("蟋蟀在戸", "きりぎりすとにあり", "戸口で秋の虫が鳴くころ"),
        Kou("霜始降", "しもはじめてふる", "霜が降り始めるころ"),
        Kou("霎時施", "こさめときどきふる", "ときどき小雨が降るころ"),
        Kou("楓蔦黄", "もみじつたきばむ", "もみじや蔦が色づくころ"),
        Kou("山茶始開", "つばきはじめてひらく", "さざんかの花が咲き始めるころ。「つばき」と読むが、さざんかのこと"),
        Kou("地始凍", "ちはじめてこおる", "大地が凍り始めるころ"),
        Kou("金盞香", "きんせんかさく", "水仙の花が咲き始めるころ。金盞は水仙のこと"),
        Kou("虹蔵不見", "にじかくれてみえず", "虹を見かけなくなるころ"),
        Kou("朔風払葉", "きたかぜこのはをはらう", "北風が木の葉を吹き払うころ"),
        Kou("橘始黄", "たちばなはじめてきばむ", "橘の実が黄色く色づき始めるころ"),
        Kou("閉塞成冬", "そらさむくふゆとなる", "天地の気がふさがり、本格的な冬になるころ"),
        Kou("熊蟄穴", "くまあなにこもる", "熊が冬ごもりのため穴に入るころ"),
        Kou("鱖魚群", "さけのうおむらがる", "鮭が群れをなして、川をさかのぼるころ"),
        Kou("乃東生", "なつかれくさしょうず", "夏枯草が芽を出すころ"),
        Kou("麋角解", "さわしかのつのおつる", "大鹿の角が落ちるころ"),
        Kou("雪下出麦", "ゆきわたりてむぎのびる", "雪の下で麦が芽を出すころ"),
        Kou("芹乃栄", "せりすなわちさかう", "せりがよく育つころ"),
        Kou("水泉動", "しみずあたたかをふくむ", "地中で凍っていた泉が動き始めるころ"),
        Kou("雉始雊", "きじはじめてなく", "雉の雄が鳴き始めるころ"),
        Kou("款冬華", "ふきのはなさく", "ふきのとうがつぼみを出すころ"),
        Kou("水沢腹堅", "さわみずこおりつめる", "沢に氷が厚く張りつめるころ"),
        Kou("鶏始乳", "にわとりはじめてとやにつく", "鶏が卵を産み始めるころ"),
    )

    /** The five seasonal festivals and the year's customs kept alike everywhere (sekku). */
    fun sekku(year: Int): List<Event> = listOf(
        Triple("nanakusa", "七草", 1 to 7) to "七草がゆを食べて、一年の無病息災を願う。五節句の一つ（人日）",
        Triple("koshougatsu", "小正月", 1 to 15) to "正月の終わりとされる日。小豆がゆを食べる習わしがある",
        Triple("hinamatsuri", "ひな祭り", 3 to 3) to "女の子の成長を願う。五節句の一つ（上巳・桃の節句）",
        Triple("hanamatsuri", "花祭り", 4 to 8) to "お釈迦さまの誕生を祝う日（灌仏会）",
        Triple("tango", "端午の節句", 5 to 5) to "男の子の成長を願う。五節句の一つ。菖蒲湯に入る習わしがある",
        Triple("tanabata", "七夕", 7 to 7) to "星に願いをかける。五節句の一つ",
        Triple("chouyou", "重陽の節句", 9 to 9) to "菊を飾って長寿を願う。五節句の一つ（菊の節句）",
        Triple("shichigosan", "七五三", 11 to 15) to "子どもの成長を祝って、神社やお寺にお参りする日",
        Triple("oomisoka", "大晦日", 12 to 31) to "一年の最後の日。年越しそばを食べる習わしがある",
    ).map { (what, note) ->
        val (key, name, md) = what
        Event(KoyomiKind.SEKKU, key, name, LocalDate.of(year, md.first, md.second), note = note)
    }

    /** The festivals of the old calendar (lunar). */
    fun lunar(year: Int): List<Event> = listOfNotNull(
        Lunisolar.dateOf(year, 8, 15)?.let { Event(KoyomiKind.LUNAR, "jugoya", "十五夜", it, note = "中秋の名月。月を眺め、すすきや団子を供える習わしがある", reading = "じゅうごや") },
        Lunisolar.dateOf(year, 9, 13)?.let { Event(KoyomiKind.LUNAR, "jusanya", "十三夜", it, note = "十五夜の次に美しいとされる「後の月」。栗や豆を供える習わしがある", reading = "じゅうさんや") },
    )

    /** Designated days (kinenbi). Checked against each body's own page on <確かめた日>. */
    val KINENBI = listOf(
        Kinenbi(1, 17, "bousai-volunteer", "防災とボランティアの日", "内閣（閣議了解）", "1995 年 1 月 17 日の阪神・淡路大震災をきっかけに定められた", "<確かめた URL>"),
        Kinenbi(1, 26, "bunkazai-bouka", "文化財防火デー", "文化庁・消防庁", "1949 年 1 月 26 日に法隆寺金堂の壁画が焼けたことから定められた", "<確かめた URL>"),
        Kinenbi(2, 2, "wetlands", "世界湿地の日", "ラムサール条約事務局", "1971 年 2 月 2 日に湿地を守るラムサール条約が採択されたことから", "<確かめた URL>"),
        Kinenbi(2, 7, "hoppou", "北方領土の日", "内閣（閣議決定）", "1855 年 2 月 7 日に日魯通好条約が結ばれたことから", "<確かめた URL>"),
        Kinenbi(3, 3, "mimi", "耳の日", "日本耳鼻咽喉科頭頸部外科学会", "「3」が耳の形に似ていることなどから", "<確かめた URL>"),
        Kinenbi(3, 21, "forests", "国際森林デー", "国際連合", "森林の大切さを知ってもらうために、国際連合が定めた", "<確かめた URL>"),
        Kinenbi(4, 7, "world-health", "世界保健デー", "世界保健機関（WHO）", "1948 年 4 月 7 日に世界保健機関ができたことから", "<確かめた URL>"),
        Kinenbi(4, 18, "hatsumei", "発明の日", "特許庁", "1885 年 4 月 18 日に今の特許法のもとになる専売特許条例が出たことから", "<確かめた URL>"),
        Kinenbi(5, 22, "biodiversity", "国際生物多様性の日", "国際連合", "1992 年 5 月 22 日に生物多様性条約の条文がまとまったことから", "<確かめた URL>"),
        Kinenbi(5, 31, "no-tobacco", "世界禁煙デー", "世界保健機関（WHO）", "たばこを吸わないことを広めるために、世界保健機関が定めた", "<確かめた URL>"),
        Kinenbi(6, 1, "kishou", "気象記念日", "気象庁", "1875 年 6 月 1 日に東京気象台が気象の観測を始めたことから", "<確かめた URL>"),
        Kinenbi(6, 5, "kankyou", "環境の日", "環境基本法", "1972 年 6 月 5 日に国連人間環境会議が開かれたことから", "<確かめた URL>"),
        Kinenbi(6, 10, "toki", "時の記念日", "生活改善同盟会", "日本書紀に、671 年のこの日に天智天皇が水時計で時を知らせたとあることから", "<確かめた URL>"),
        Kinenbi(7, 1, "kokumin-anzen", "国民安全の日", "内閣（閣議了解）", "産業や交通などの事故を防ぐことを広めるために定められた", "<確かめた URL>"),
        Kinenbi(7, 11, "population", "世界人口デー", "国連人口基金（UNFPA）", "1987 年 7 月 11 日に世界の人口が 50 億人になったとされることから", "<確かめた URL>"),
        Kinenbi(8, 1, "mizu", "水の日", "水循環基本法", "水の大切さを知ってもらうために、法律で定められた", "<確かめた URL>"),
        Kinenbi(8, 10, "michi", "道の日", "国土交通省（もとは建設省）", "1920 年 8 月 10 日に日本で初めての道路の改良計画が始まったことから", "<確かめた URL>"),
        Kinenbi(9, 1, "bousai", "防災の日", "内閣（閣議了解）", "1923 年 9 月 1 日の関東大震災と、台風が多い時期であることから", "<確かめた URL>"),
        Kinenbi(9, 21, "peace", "国際平和デー", "国際連合", "世界の停戦と非暴力を呼びかける日として、国際連合が定めた", "<確かめた URL>"),
        Kinenbi(10, 1, "hou", "法の日", "法務省ほか（閣議決定）", "1928 年 10 月 1 日に陪審法が施行されたことから", "<確かめた URL>"),
        Kinenbi(10, 14, "tetsudou", "鉄道の日", "国土交通省（もとは運輸省）", "1872 年 10 月 14 日に新橋と横浜の間に鉄道が開業したことから", "<確かめた URL>"),
        Kinenbi(11, 1, "keiryou", "計量記念日", "経済産業省", "1993 年 11 月 1 日に今の計量法が施行されたことから", "<確かめた URL>"),
        Kinenbi(11, 9, "119", "119番の日", "消防庁", "消防の電話番号「119」にちなんで", "<確かめた URL>"),
        Kinenbi(12, 1, "aids", "世界エイズデー", "世界保健機関（WHO）", "エイズへの理解を広めるために、世界保健機関が定めた", "<確かめた URL>"),
        Kinenbi(12, 10, "human-rights", "人権デー", "国際連合", "1948 年 12 月 10 日に世界人権宣言が採択されたことから", "<確かめた URL>"),
    )

    fun kinenbi(year: Int): List<Event> = KINENBI.map {
        Event(KoyomiKind.KINENBI, it.key, it.name, LocalDate.of(year, it.month, it.day), note = it.note, designatedBy = it.designatedBy)
    }

    /** Days kept differently by place (regional). The places are typical ones, not all of them. */
    val REGIONAL = listOf(
        Regional("bon-july", "お盆（7 月）", "おぼん", "東京・横浜・静岡市などの一部の地域",
            "先祖の霊を迎えてまつる。7 月 13 日から 16 日に行う地域がある", "<確かめた URL>") { y -> LocalDate.of(y, 7, 13) to LocalDate.of(y, 7, 16) },
        Regional("bon-august", "お盆（8 月）", "おぼん", "全国の多くの地域",
            "先祖の霊を迎えてまつる。月遅れの 8 月 13 日から 16 日に行う地域が多い", "<確かめた URL>") { y -> LocalDate.of(y, 8, 13) to LocalDate.of(y, 8, 16) },
        Regional("bon-lunar", "旧盆", "きゅうぼん", "沖縄・奄美など",
            "旧暦の 7 月 13 日から 15 日に、先祖の霊を迎えてまつる", "<確かめた URL>") { y ->
            Lunisolar.dateOf(y, 7, 13)?.let { it to it.plusDays(2) }
        },
        Regional("jizobon", "地蔵盆", "じぞうぼん", "京都・大阪など近畿地方を中心とした地域",
            "子どもの健やかな成長を願って、町内のお地蔵さまをまつる", "<確かめた URL>") { y -> LocalDate.of(y, 8, 23) to LocalDate.of(y, 8, 24) },
        Regional("tookanya", "十日夜", "とおかんや", "関東・甲信越など東日本の地域",
            "旧暦 10 月 10 日の収穫を祝う行事。わらの束で地面を打つ習わしがある", "<確かめた URL>") { y ->
            Lunisolar.dateOf(y, 10, 10)?.let { it to it }
        },
        Regional("inoko", "亥の子", "いのこ", "中国・四国・九州など西日本の地域",
            "旧暦 10 月の亥の日に、収穫を祝い無病息災を願う行事。亥の子餅を食べる習わしがある", "<確かめた URL>") { y ->
            Lunisolar.dateOf(y, 10, 1)?.let { first -> (0L..11L).map { first.plusDays(it) }.first { Kanshi.branch(it) == 11 } }?.let { it to it }
        },
    )

    fun regional(year: Int): List<Event> = REGIONAL.mapNotNull { r ->
        r.days(year)?.let { (start, end) -> Event(KoyomiKind.REGIONAL, r.key, r.name, start, end, r.note, r.reading, regions = r.regions) }
    }
}
```

地域ごとの行事の `source` も、Step 3 と同じに確かめた資料（自治体・博物館・民俗学の辞典などの頁）の URL にする。

- [ ] **Step 5: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test`、`grep -n "確かめた" kit-calendar/src/main`
Expected: テストは PASS。grep は KDoc の「Checked against … on <日付>」の 1 行だけ（`<確かめた URL>` も `<確かめた日>` も残っていない）

- [ ] **Step 6: コミット**

```bash
git add kit-calendar
git commit -m "feat(kit-calendar): 七十二候・五節句と年中行事・記念日・地域ごとの行事の表（REQ-KOYOMI F-6・F-7）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: その日の暦の事実を集める（F-1〜F-4・F-8）と版上げ

**Files:**
- Create: `<pkg>/Koyomi.kt`
- Create: `kit-calendar/README.md`
- Modify: `README.md`（モジュールの表に 1 行）、`gradle.properties`（`yorisoiVersion=2.32.0`）
- Test: `<test>/KoyomiTest.kt`

**Interfaces:**
- Consumes: Task 1〜6 のすべて
- Produces: `sealed interface Relation { data object Today; data class DayOf(val day: Int); data class DaysAhead(val days: Int); data object Yesterday }`、`data class KoyomiFact(id, kind, name, reading, date, relation, note, designatedBy, regions) { val certainty }`、`class Koyomi { fun on(date: LocalDate): List<KoyomiFact> }`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KoyomiTest {
    private val koyomi = Koyomi()
    private fun on(y: Int, m: Int, d: Int) = koyomi.on(LocalDate.of(y, m, d))
    private fun List<KoyomiFact>.of(kind: KoyomiKind) = filter { it.kind == kind }

    @Test
    fun `15 November 2026 - 立冬 9th day, 地始凍, 七五三 today, 仏滅 (AC-2)`() {
        val facts = on(2026, 11, 15)
        val sekki = facts.of(KoyomiKind.SEKKI).single()
        assertEquals("立冬", sekki.name)
        assertEquals(Relation.DayOf(9), sekki.relation)
        assertEquals("sekki:rittou:2026-11-07", sekki.id)
        assertEquals("地始凍", facts.of(KoyomiKind.KOU).single().name)
        assertEquals(Relation.Today, facts.of(KoyomiKind.SEKKU).single { it.name == "七五三" }.relation)
        assertEquals("仏滅", facts.of(KoyomiKind.ROKUYOU).single().name)
        assertEquals(Certainty.TRADITION, facts.of(KoyomiKind.ROKUYOU).single().certainty)
    }

    @Test
    fun `小雪 is 1 day ahead on the 21st and today on the 22nd (AC-2)`() {
        assertEquals(Relation.DaysAhead(1), on(2026, 11, 21).of(KoyomiKind.SEKKI).single { it.name == "小雪" }.relation)
        val today = on(2026, 11, 22).of(KoyomiKind.SEKKI)
        assertEquals(listOf("小雪"), today.map { it.name })
        assertEquals(Relation.Today, today.single().relation)
    }

    @Test
    fun `the first days of a year are counted from the last 冬至`() {
        val sekki = on(2027, 1, 1).of(KoyomiKind.SEKKI).single()
        assertEquals("冬至", sekki.name)
        assertEquals(Relation.DayOf(11), sekki.relation)
    }

    @Test
    fun `days ahead reach into the next year - 元日 is 2 days ahead on 30 December`() {
        val holiday = on(2026, 12, 30).of(KoyomiKind.HOLIDAY).single { it.name == "元日" }
        assertEquals(Relation.DaysAhead(2), holiday.relation)
        assertEquals(LocalDate.of(2027, 1, 1), holiday.date)
    }

    @Test
    fun `a period counts its days, a past one-day thing is yesterday, a designated day only today`() {
        val higan = on(2026, 3, 19).of(KoyomiKind.ZASSETSU).single { it.name == "春の彼岸" }
        assertEquals(Relation.DayOf(3), higan.relation)
        assertEquals(Relation.Yesterday, on(2026, 11, 16).of(KoyomiKind.SEKKU).single { it.name == "七五三" }.relation)
        assertTrue(on(2026, 9, 2).of(KoyomiKind.KINENBI).isEmpty())
        val bousai = on(2026, 9, 1).of(KoyomiKind.KINENBI).single()
        assertEquals("内閣（閣議了解）", bousai.designatedBy)
        assertEquals(Certainty.DESIGNATED, bousai.certainty)
    }

    @Test
    fun `the moon only a day either side - full moon 24 November 2026`() {
        assertEquals(Relation.Today, on(2026, 11, 24).of(KoyomiKind.MOON).single().relation)
        assertEquals(Relation.DaysAhead(1), on(2026, 11, 23).of(KoyomiKind.MOON).single().relation)
        assertTrue(on(2026, 11, 21).of(KoyomiKind.MOON).isEmpty())
    }

    @Test
    fun `regional days say where (D-6)`() {
        val bon = on(2026, 8, 14).of(KoyomiKind.REGIONAL).single { it.name == "お盆（8 月）" }
        assertEquals(Relation.DayOf(2), bon.relation)
        assertEquals("全国の多くの地域", bon.regions)
    }

    @Test
    fun `a day is worked out once (F-8)`() {
        assertSame(koyomi.on(LocalDate.of(2026, 11, 15)), koyomi.on(LocalDate.of(2026, 11, 15)))
    }
}
```

（2026-11-24 の満月は phases.tsv の 2026 年の「11 24 23 54」。2026-11-23 は「あと 1 日」）

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: コンパイルエラー（`Koyomi`・`KoyomiFact`・`Relation` がない）

- [ ] **Step 3: `Koyomi.kt` を書く**

```kotlin
package io.github.duwenji.yorisoi.kit.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** How a fact stands to the day asked about (REQ-KOYOMI F-3). */
sealed interface Relation {
    /** It is today, or begins today. */
    data object Today : Relation

    /** The [day]-th day (2 or more) of something that began on an earlier day. */
    data class DayOf(val day: Int) : Relation

    /** It comes in [days] days (1–3). */
    data class DaysAhead(val days: Int) : Relation

    /** It was yesterday (a one-day thing). */
    data object Yesterday : Relation
}

/**
 * One thing the calendar has to say of a day (REQ-KOYOMI F-2): [note] is the operator's line on it
 * (D-3); [designatedBy] who set a designated day, [regions] where a regional day is kept.
 */
data class KoyomiFact(
    val id: String,
    val kind: KoyomiKind,
    val name: String,
    val reading: String?,
    val date: LocalDate,
    val relation: Relation,
    val note: String,
    val designatedBy: String? = null,
    val regions: String? = null,
) {
    val certainty: Certainty get() = kind.certainty
}

/**
 * The calendar's facts for a day (REQ-KOYOMI F-1, F-4): the solar term and the pentad now (and the
 * next term 1–3 days ahead); the seasonal days, festivals, holidays and regional days of today, 1–3
 * days ahead and yesterday (or of a period that has begun); the moon's quarters a day either side;
 * today's designated day and rokuyou. Japan's dates; no network. One day is kept (F-8).
 */
class Koyomi {
    @Volatile
    private var kept: Pair<LocalDate, List<KoyomiFact>>? = null

    fun on(date: LocalDate): List<KoyomiFact> {
        kept?.let { (day, facts) -> if (day == date) return facts }
        return facts(date).also { kept = date to it }
    }

    private fun facts(d: LocalDate): List<KoyomiFact> {
        val years = d.year - 1..d.year + 1
        val out = mutableListOf<KoyomiFact>()
        fun add(events: List<Event>, ahead: Boolean, yesterday: Boolean, filter: (Relation) -> Boolean = { true }) {
            for (e in events) relation(e, d, ahead, yesterday)?.takeIf(filter)?.let { out += fact(e, it) }
        }
        // ① The solar term now, and the next one 1–3 days ahead.
        val terms = years.flatMap(SolarTerms::of)
        add(terms.zipWithNext { (t, at), (_, next) ->
            Event(KoyomiKind.SEKKI, t.key, t.label, Astro.dateOf(at), Astro.dateOf(next).minusDays(1), t.note, t.reading)
        }, ahead = true, yesterday = false)
        // ② The pentad now. A year's list starts at 小寒's first pentad (285°), the 67th from 立春's.
        val pentads = years.flatMap(::pentadsOf)
        add(pentads.withIndex().zipWithNext { (i, at), (_, next) ->
            val k = Math.floorMod(i - 6, 72)
            val kou = Tables.KOU[k]
            Event(KoyomiKind.KOU, "${k + 1}", kou.name, Astro.dateOf(at), Astro.dateOf(next).minusDays(1), kou.note, kou.reading)
        }, ahead = false, yesterday = false)
        // ③ Seasonal days, festivals, holidays, regional days.
        add(years.flatMap(Zassetsu::of), ahead = true, yesterday = true)
        add(years.flatMap(Tables::sekku), ahead = true, yesterday = true)
        add(years.flatMap(Tables::lunar), ahead = true, yesterday = true)
        add(years.filter { it >= 2016 }.flatMap(Holidays::of), ahead = true, yesterday = true)
        add(years.flatMap(Tables::regional), ahead = true, yesterday = true)
        // ④ The moon's quarters a day either side.
        val quarters = Astro.moonQuarters(d.minusDays(10).atStartOfDay().toInstant(Astro.JST), d.plusDays(10).atStartOfDay().toInstant(Astro.JST))
        add(quarters.map { (q, at) -> MOON[q].let { (key, name, note) -> Event(KoyomiKind.MOON, key, name, Astro.dateOf(at), note = note) } },
            ahead = true, yesterday = true) { it !is Relation.DaysAhead || it.days == 1 }
        // ⑤ Today's designated day, ⑥ today's rokuyou.
        add(Tables.kinenbi(d.year), ahead = false, yesterday = false) { it == Relation.Today }
        val r = Rokuyou.of(d)
        out += fact(Event(KoyomiKind.ROKUYOU, r.key, r.label, d, note = r.note, reading = r.reading), Relation.Today)
        return out
    }

    /** The 72 pentads' beginnings in [year], from 小寒's first (285°) on; kept per year. */
    private fun pentadsOf(year: Int): List<Instant> = PENTADS.getOrPut(year) { Astro.sunSteps(285.0, 5.0, 72, Astro.startOf(year)) }

    private fun fact(e: Event, r: Relation) =
        KoyomiFact(e.id, e.kind, e.name, e.reading, e.start, r, e.note, e.designatedBy, e.regions)

    private fun relation(e: Event, d: LocalDate, ahead: Boolean, yesterday: Boolean): Relation? {
        val toStart = ChronoUnit.DAYS.between(d, e.start)
        return when {
            toStart == 0L -> Relation.Today
            toStart < 0 && !d.isAfter(e.end) -> Relation.DayOf((1 - toStart).toInt())
            ahead && toStart in 1..3 -> Relation.DaysAhead(toStart.toInt())
            yesterday && e.start == e.end && toStart == -1L -> Relation.Yesterday
            else -> null
        }
    }

    private companion object {
        val PENTADS = ConcurrentHashMap<Int, List<Instant>>()

        val MOON = listOf(
            Triple("new", "新月", "月が太陽と同じ方向にあって、見えない"),
            Triple("first-quarter", "上弦の月", "右半分が光る半月。夕方の南の空に見える"),
            Triple("full", "満月", "夜通し見える丸い月"),
            Triple("last-quarter", "下弦の月", "左半分が光る半月。明け方の南の空に見える"),
        )
    }
}
```

注意: ② の七十二候は、年ごとに 285°（小寒の初候）から 72 個を取る。`pentads` は 3 年分を並べたもの（216 個）で、216 は 72 の倍数なので、`floorMod(i - 6, 72)` で年をまたいでも同じ候になる。小寒の初候（i = 0）は KOU[66]（芹乃栄）。

- [ ] **Step 4: 通ることを確かめる**

Run: `./gradlew :kit-calendar:test`
Expected: PASS

- [ ] **Step 5: README と版**

`kit-calendar/README.md`:

```markdown
# kit-calendar

その日に話せる暦の事実を返す部品です。両アプリの雑談の話題「暦」で使います。要件は [REQ-KOYOMI](../docs/requirements/REQ-KOYOMI.md)。

| 部品 | 内容 |
|---|---|
| `Koyomi` | `on(date)` が、その日の暦の事実（`KoyomiFact`）の一覧を返す。1 日分を覚えておく |
| `KoyomiFact` | 名前・読み・日付・今日との関係（`Relation`）・説明・種類（`KoyomiKind`）・確かさ（`Certainty`）・定めた団体・行う地域 |
| `KoyomiKind` | 暦の種類。`json` は `channel.json` の綴り（`sekki`・`kou`・…） |
| `SolarTerms`・`Lunisolar`・`Rokuyou` | 二十四節気の時刻、旧暦の日付、六曜 |

## 使い方

```kotlin
val koyomi = Koyomi()
val facts = koyomi.on(LocalDate.now(ZoneOffset.ofHours(9)))
```

## 依存

天文の計算に Astronomy Engine（`io.github.cosinekitty:astronomy:2.1.19`、MIT ライセンス）を使います。Maven Central にないので、使う側の `settings.gradle.kts` にも、JitPack をこの group だけに絞って足してください。

```kotlin
exclusiveContent {
    forRepository { maven("https://jitpack.io") }
    filter { includeGroup("io.github.cosinekitty") }
}
```

## 確かめ方

計算は、国立天文台の暦要項（2016〜2027 年）と内閣府の祝日の一覧とテストで照らしています（`src/test/resources/rekiyou/`）。毎年 2 月に翌年の暦要項が出たら、`tools/rekiyou_fixtures.py` で表を作り直して、その年を照らしてください（REQ-KOYOMI N-5）。
```

`README.md` のモジュールの表の `kit-songs` の行の次に足す:

```markdown
| [`kit-calendar`](kit-calendar/README.md) | JVM | その日の暦の事実（二十四節気・七十二候・雑節・五節句と年中行事・旧暦の行事・祝日・月の満ち欠け・六曜・記念日・地域ごとの行事）。REQ-KOYOMI。使う側で JitPack（Astronomy Engine）が要る |
```

`gradle.properties` の `yorisoiVersion=2.31.0` を `yorisoiVersion=2.32.0` にする（新しいモジュールを足すのでマイナー版）。

- [ ] **Step 6: platform 全体のテスト**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL（ほかのモジュールに影響がないこと）

- [ ] **Step 7: コミット**

```bash
git add kit-calendar README.md gradle.properties
git commit -m "feat(kit-calendar): その日の暦の事実を集める Koyomi（REQ-KOYOMI F-1〜F-4・F-8、v2.32.0）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 終わったあと

- platform の main へのマージ・push・タグ `v2.32.0` の push（公開）は、開発者の確認のあとで
- 両アプリで使うときは、アプリの `settings.gradle.kts` にも JitPack の `exclusiveContent` が要る（X-5・X-6 の計画で入れる）
