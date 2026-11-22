# API-CHANNEL-PACKAGE の改訂案（ADR-AGENT）

## channel.json の項目の表に足す行

| 項目 | 型 | 必須 | 内容・とりうる値 |
|---|---|---|---|
| `agent.mode` | string | 任意 | `off`（準備役を使わない）/ `plan`（使う）。書かなければ `off` |
| `agent.web` | string | 任意 | `off`（ページを読まない）/ `on`（台帳とチャンネルの出どころを読む）。書かなければ `on` |
| `agent.scenes` | string[] | 任意 | 調べてよい場面。[場面](#場面)から。書かなければ全部 |
| `agent.paths` | string[] | 任意 | 目指す筋の型。`spot` 以外の場面を `>` でつないだ 2〜4 個。0〜5 個 |
| `agent.sources` | object[] | 任意 | チャンネルの出どころ。0〜10 個。1 つは `name`（1〜20 文字）・`urls`（`https://` で始まる 1〜3 個）・`scenes`。書けるのはチャンネル提供者自身のサイトか公の出どころのページだけ（審査で確かめる） |
| `agent.skip` | string[] | 任意 | 使わない台帳の出どころの種類（[出どころの種類](#出どころの種類)）か、読まないドメイン・URL の前方一致。0〜20 個。運営の除外リストに足すだけで、減らせない |
| `agent.topics` | string[] | 任意 | 調べ物に足す言葉。0〜10 個、1 つ 20 文字まで。URL と `{{` を含めない |

## 足す節

### 場面

| 値 | 場面 | 使えるとき |
|---|---|---|
| `calendar` | 暦 | `talk.events.calendar` が真 |
| `season` | 季節の様子 | いつでも |
| `livelihood` | 暮らし・なりわい | いつでも |
| `events` | 地域の催し | `talk.events.localEvent` が真 |
| `spot` | スポット | `talk.events.spot` が真 |

### 出どころの種類

`national`（国の機関）・`prefecture`（都道府県）・`municipality`（市区町村）・`tourism`（観光協会）・`association`（農協・漁協などの団体）。チャンネルの出どころは `channel`。

## アプリでの知らない値の表に足す行

| 項目 | 知らない値があるとき |
|---|---|
| `agent.mode`・`agent.web` | `off` として扱う |
| `agent.scenes` | その値を使わない |
| `agent.paths` | 知らない場面を含む型を使わない |
| `agent.skip` の出どころの種類 | 台帳の出どころを使わない（チャンネルの出どころだけを読む） |

## 確認観点・エラー表

- 拒む（`bad_value`）: 知らない値、`scenes` にない場面か `spot` を含む `paths`、形の誤った `sources`、除外リストに当たる `sources`、数・長さを超える値
- 警告: 今の項目（`talk.events`・`talk.level`）で使えない場面を書いたもの、準備しないチャンネル（`talk.level` が `quiet`、または `chat` と `spot` がどちらも偽）の `mode: plan`

## 互換性方針・移行メモに足す文

- 準備役（`agent`。ADR-AGENT）は、`agent` を読める `core-format` のアプリだけが使う。それより前のアプリは `agent` を無視して準備役を使わないので、`agent` を書いたチャンネルを古いアプリの一覧に出しても制限は緩まず、必要な版（API-CHANNEL-LIST P-20）を上げなくてよい

## プロンプトのスロットに足す文

- 準備役の指示（`agent/system`・`agent/user`）にはチャンネルのスロットを差し込まない。`companion/topics` だけを、データの区切りで囲んで準備の入力に渡す（ADR-AGENT A-4・A-42）
