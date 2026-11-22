# core-format の改訂案（ADR-AGENT）

core-format（JVM の部品）に足すもの。形の正本は [API-CHANNEL-PACKAGE の改訂案](API-CHANNEL-PACKAGE.md)。

| No. | 足すもの | 中身 | ADR-AGENT |
|---|---|---|---|
| CF-1 | 語彙: 場面 | `calendar`・`season`・`livelihood`・`events`・`spot`。共通の一覧（ECOSYSTEM E-9）に足す。console の `vocabulary.json` に写し、`VocabularyTest` で比べる | A-6・A-52 |
| CF-2 | 語彙: 出どころの種類 | `national`・`prefecture`・`municipality`・`tourism`・`association`（台帳）と `channel`（チャンネルの出どころ） | A-21・A-23 |
| CF-3 | 語彙: 除外の種類 | [web-exclusion.schema.json](../data/web-exclusion.schema.json) の `kind` の 9 つ | A-24 |
| CF-4 | `channel.json` の `agent` を読む | `mode`・`web`・`scenes`・`paths`・`sources`・`skip`・`topics` の型（`StationManifest`） | A-6 |
| CF-5 | `StationValidator` の確かめ | 拒む: 知らない値、`scenes` にない場面か `spot` を含む `paths`、形の誤った `sources`（名前 1〜20 字・`https://` の URL 1〜3 個・場面）、数・長さを超える値。除外リストに当たる `sources` は、呼ぶ側（console）が除外リストを渡したときに拒む。警告: A-7 で外れる場面を書いたもの、準備しないチャンネルの `plan` | A-9 |
| CF-6 | アプリでの知らない値 | `mode`・`web` の知らない値は `off`、知らない場面とそれを含む `paths` は使わない、`skip` の知らない種類があれば台帳の出どころを使わない | A-8 |
| CF-7 | 機械の確認を core-text に移す | `Checks.kt` の URL などの確かめを core-text に移し、core-format は core-text を使う（振る舞いは変えない） | A-52（core-text） |
| CF-8 | マイナー版 | 項目と語彙の追加なので、`format` は上げず、core-format のマイナー版を上げる | A-8 |
