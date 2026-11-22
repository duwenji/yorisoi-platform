# kit-calendar

その日に話せる暦の事実を返す部品です。両アプリの雑談の話題「暦」で使います。要件は [REQ-KOYOMI](../docs/requirements/REQ-KOYOMI.md)。

| 部品 | 内容 |
|---|---|
| `Koyomi` | `on(date)` が、その日の暦の事実（`KoyomiFact`）の一覧を返す。1 日分を覚えておく |
| `KoyomiFact` | 名前・読み・日付・今日との関係（`Relation`）・説明・種類（`KoyomiKind`）・確かさ（`Certainty`）・定めた団体・行う地域 |
| `KoyomiKind` | 暦の種類。`json` は `channel.json` の綴り（`sekki`・`kou`・…） |
| `SolarTerms`・`Lunisolar`・`Rokuyou` | 二十四節気の時刻、旧暦の日付、六曜 |
| `SeasonObsFile` | 気象庁の季節の観測の配信ファイル（[season-obs.schema.json](../docs/contracts/api/season-obs.schema.json)）の読み取り。`around(prefecture, today)` が、今いる都道府県と隣の都道府県の 14 日以内の観測を、自分の県を先に・新しい順に 20 件まで返す（準備役の道具 `season_obs`。[ADR-AGENT](../docs/decisions/ADR-AGENT.md) A-12・A-27・A-28） |
| `SeasonObservation` | 観測の 1 件。`fact()` が、値を変えずに 60 字以内の 1 文にする（「福井地方気象台で 11月22日に初雪を観測、平年より 11 日早い、昨年より 11 日早い」） |
| `Prefectures` | 47 都道府県の隣り合い（陸の境と、青函・アクアライン・淡路・瀬戸大橋・しまなみ・関門） |

## 使い方

```kotlin
val koyomi = Koyomi()
val facts = koyomi.on(LocalDate.now(ZoneOffset.ofHours(9)))
```

## 依存

季節の観測のファイルを読むのに org.json を使います（Android には入っています。ほかで使うときは足してください）。天文の計算に Astronomy Engine（`io.github.cosinekitty:astronomy:2.1.19`、MIT ライセンス）を使います。Maven Central にないので、使う側の `settings.gradle.kts` にも、JitPack をこの group だけに絞って足してください。

```kotlin
exclusiveContent {
    forRepository { maven("https://jitpack.io") }
    filter { includeGroup("io.github.cosinekitty") }
}
```

## 確かめ方

計算は、国立天文台の暦要項（2016〜2027 年）と内閣府の祝日の一覧とテストで照らしています（`src/test/resources/rekiyou/`）。毎年 2 月に翌年の暦要項が出たら、`tools/rekiyou_fixtures.py` で表を作り直して、その年を照らしてください（REQ-KOYOMI N-5）。記念日と地域ごとの行事は、出どころの頁を `Tables.kt` に書いてあります。
