# kit-songs

運営が配る季節の歌（[API-SONGS](../docs/contracts/API-SONGS.md)）のアプリ側の部品です。両アプリで使います。要件は [REQ-NEARBY-TALK](../docs/requirements/REQ-NEARBY-TALK.md) F-21〜F-27・F-33・F-34。

| 部品 | 内容 |
|---|---|
| `SongLibrary` | 一覧（`catalog.json`）と音を取って端末に残す。曲を選ぶ。前回と今回の散歩・走行で流した曲を覚える |
| `SongCatalog`・`Song` | 一覧の形。読めない一覧は使わず、項目のおかしい曲だけを外す |
| `SongSeason` | 端末の日付から季節（3〜5 月 春、6〜8 月 夏、9〜11 月 秋、12〜2 月 冬） |
| `SongPlayer` | 歌を流す・止める。読み上げより小さい音量（0.6）。止めるときは 1 秒で小さくする |

## 使い方

```kotlin
val songs = SongLibrary(File(context.filesDir, "songs"), baseUrl = /* スポットのタイルと同じ基点 */, userAgent = "SanpoGuide/1.9.0")
val player = SongPlayer(context)

// 散歩・走行をしていないときに（WorkManager など）
songs.refresh()                 // 1 日 1 回だけ問い合わせる（ETag つき）
if (unmetered) songs.prefetch() // 今の季節・次の季節・通年の音を取る（sha256 を確かめる。合わせて 10 MB まで）

// 散歩・走行の始まり
songs.startWalk()

// 雑談の話題として歌を流すとき
val song = songs.pick(timeOfDay = "evening", weather = "clear", exclude = songs.recentlyPlayed())
val file = song?.let(songs::fileOf)
if (file != null && player.mayPlay()) {
    songs.played(song)
    player.play(file)           // 終わるまで待つ
}

// ほかに話すことができたら
player.stop()
```

- `refresh`・`prefetch` は、呼ばれたときにだけ通信します。散歩・走行の最中には呼ばないでください（AS-1）
- 送るのは URL と `User-Agent`・`If-None-Match` だけです。位置は送りません
- `pick` は、端末に音がある曲だけから選びます。季節が合う曲（通年を含む）のうち、時間帯・天気が合うもの、今の季節だけのものを先にし、同じくらいなら無作為に選びます
- `SongPlayer.mayPlay` は、ほかのアプリが音楽を流しているときと通話中は偽です（F-26）。流している間は音声フォーカスを取り（`GAIN_TRANSIENT_MAY_DUCK`）、ほかの音は小さくなります
- 「このアプリについて」には、`catalog.credit` と、曲ごとの `title`・`lyricist`・`composer`・`notesFrom` を出します（AS-6）
