# スポットのタイル 配信契約書

## 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | API-SPOT-TILES |
| ドキュメント種別 | API契約書 |
| 対象システム/機能 | yorisoi エコシステムのアプリ（`kit-geo`）と、運営のスポットのタイルの配信（[API-SPOT-TILES](../contracts/API-SPOT-TILES.md)） |
| 承認者 | 開発者 |
| ステータス | 承認済 |

本書は、運営が作るスポットのタイルの置き場所・形式・取り方を定めます。タイルを作るバッチと配信の AWS の構成は、yorisoi-console の文書で定めます。

## 目的・背景

API-SPOT-TILES で、アプリはスポット・施設・お店・信号を、公開の Overpass に問い合わせる代わりに、運営が週 1 回作る geohash 5 桁のタイルから取ることにした。本書は、作る側（バッチ）と読む側（`kit-geo`）が同じものを前提にできるよう、次のことを定める。

1. タイルの URL と、層ごとの JSON の形
2. どの OSM の要素を、どの形で入れるか（今の `OverpassClient` と同じ答えになるように）
3. 取り直し（ETag）と、取れないときのアプリの動き
4. 互換性とライセンスの表示

## スコープ・非スコープ

スコープ:
- タイルの URL・層・JSON の形・HTTP の応答
- 抜き出しの条件と、位置・輪郭の作り方
- アプリの取り方・残し方・予備の Overpass に戻る条件
- 互換性の方針

非スコープ:
- バッチと配信の AWS の構成・配備・警報（yorisoi-console の文書）
- スポットの種類の表示名や、話す判断での使い方（今と同じく `kit-geo`・`kit-companion` で決める）
- スポットの写真（`SpotPhotos`）

## 対応元ID（トレーサビリティ）

| 対応元ID | 内容 | 対応状況 |
|---|---|---|
| API-SPOT-TILES S-2 | geohash 5 桁、中心があるタイルに丸ごと入れる | [タイルの単位と位置](#タイルの単位と位置) |
| API-SPOT-TILES S-3 | 基本とお店の 2 層 | [URL と層](#url-と層) |
| API-SPOT-TILES S-4 | 今の Overpass の条件、上限なし、座標 5 桁、輪郭 5m | [抜き出しの条件](#抜き出しの条件)・[JSON の形](#json-の形) |
| API-SPOT-TILES S-5 | 日本全体、週 1 回 | ST-3・ST-4 |
| API-SPOT-TILES S-8 | 版を含まない URL、ETag で取り直し | ST-5・[HTTP の応答](#http-の応答) |
| API-SPOT-TILES S-9 | 円にかかるタイル、端末で今と同じ距離に絞る、取れないときは Overpass | [アプリの取り方](#アプリの取り方) |
| API-SPOT-TILES S-10 | 端末に残す期間、先読み | [アプリの取り方](#アプリの取り方) |
| API-SPOT-TILES S-12 | 運営に届くのはタイルの番号だけ | ST-7 |
| API-SPOT-TILES S-13 | ODbL 1.0 と出典 | ST-8 |

## スポットのタイルの方針

### タイル

| No. | 論点 | 決定 |
|---|---|---|
| S-1 | 取り先 | 運営が地域ごとのタイルを前もって作り、静的なファイルとして配る。アプリはまずタイルを使い、取れないときだけ今の Overpass に問い合わせる（S-9） |
| S-2 | タイルの単位 | geohash 5 桁（日本の緯度で約 4km × 5km）。1 枚に、その範囲に中心がある要素をすべて入れる。公園などの輪郭は、中心があるタイルに丸ごと入れる |
| S-3 | 層 | 1 枚のタイルを 2 つの層に分けて配る。**基本**: スポット・施設（`FacilityKind` のすべて）・信号。**お店**: 店の種類（API-CHANNEL-PACKAGE の `restaurant`〜`shop`）。お店の層は、チャンネルが店の種類を探すとき（`searchedKinds` が空でないとき）だけ取る。都心ではお店が約半分を占めるため |
| S-4 | 中身 | 今の `OverpassClient.query` と同じ条件で抜き出す（名前のあるスポットの種類、施設の種類と `access` の除外、チェーン店の除外）。ただし件数の上限（`out geom 80` など）はかけない。スポットとお店は OSM のタグをそのまま持つ（AI の根拠と写真の手がかりに使うため）。施設は種類・位置・名前だけ持つ。座標は小数 5 桁、輪郭は 5m で間引く。アプリの種類の判定（`categoryOf`・`FacilityKind.of`）は今のまま端末で行う |
| S-5 | 範囲と新しさ | 日本全体。週に 1 回作り直す。日本全体でも gzip 後で約 100MB なので、地域を絞らない |

### 作り方と配り方

| No. | 論点 | 決定 |
|---|---|---|
| S-6 | 作り方 | Geofabrik の日本の抽出（`.osm.pbf`）を週 1 回取り、osmium で S-4 の条件に合う要素を抜き出し、輪郭を組み立て、タイルごとの JSON に変える。Overpass は使わない（約 1.9 万枚を取ると、混雑と利用の制限で取りきれない）。バッチは EventBridge Scheduler から ECS Fargate で動かす。作り終えたら前の週のタイルと比べ、枚数や合計の大きさが大きく減っていれば差し替えずに運営に知らせる |
| S-7 | 置き場所 | 運営の AWS アカウント（ap-northeast-1、開発用と本番用）に、管理システムとも運営の AI とも別のスタックとして置く。S3 に置き、CloudFront で配る。誰でも読める（OSM のデータで、秘密がない）。コードと配備は yorisoi-console の `infra/` に置き、CDK と GitHub Actions の配備の仕組みを共用する |
| S-8 | 版と取り直し | タイルの URL は版を含まない固定の形にし、作り直しで中身が変わったタイルだけ `ETag` が変わる。アプリは残っているタイルを、`If-None-Match` で取り直す（変わっていなければ 304 で中身を送らない） |

### アプリ

| No. | 論点 | 決定 |
|---|---|---|
| S-9 | 取り方と予備 | `kit-geo` に、タイルからスポットを取る部品を足す。検索の中心から今の検索の半径（600m）の円にかかるタイル（1〜4 枚）を取り、端末で今と同じ距離（スポット 600m、近くだけの施設 250m、お店 400m）に絞って、今と同じ `NearbyResult` にする。タイルが取れず端末にも残っていないとき、またはタイルのない場所（日本の外。404）では、今の Overpass に問い合わせる |
| S-10 | 端末に残すもの | `SpotCache` は、Overpass の答えの代わりにタイルを残す。設定の「残す・期間・消す」の意味は変えない（期間を過ぎたタイルは S-8 で取り直し、取れなければ最長 90 日まで使う）。よく通る道の先読み（REQ-FREQUENT-WAYS F-6）は、道にかかるタイルを取るだけにする。間を 10 秒空ける必要はなくなる |
| S-11 | 新しさの表示 | 開発者モードの記録の `source` に、タイルから取ったことを足す（Overpass・残っていたタイル・取り直したタイルを分ける） |

### 情報とライセンス

| No. | 論点 | 決定 |
|---|---|---|
| S-12 | 配信に届く情報 | 運営に届くのは、取られたタイルの番号（約 4km × 5km の範囲）と IP アドレスだけ。CloudFront のアクセスログは有効にせず、要求の数などの集計（CloudWatch のメトリクス）だけ見る。今の Overpass には検索の中心の正確な緯度経度が第三者に届いているので、それよりも届く情報は少なくなる。Overpass に問い合わせる予備の経路（S-9）は今と同じ |
| S-13 | OSM のライセンス | タイルは OSM から作ったデータベースとして公開することになるので、ODbL 1.0 で配る。配信の入口に、ライセンスと出典（© OpenStreetMap contributors）を書いたファイルを置く。アプリの出典表示（設定画面）は今のまま |

```mermaid
flowchart LR
  subgraph BATCH["運営の AWS（週 1 回）"]
    GF["Geofabrik<br/>日本の .osm.pbf"] --> F["Fargate<br/>osmium で抜き出し<br/>タイルの JSON に変換"]
    F --> S3[("S3<br/>geohash5 × 基本・お店")]
    S3 --> CF["CloudFront"]
  end
  subgraph APP["アプリ（kit-geo）"]
    R["SpotRepository"] --> C[("端末のタイル<br/>SpotCache")]
  end
  CF -- "タイルの番号だけ<br/>ETag で取り直し" --> R
  R -. "取れないとき・日本の外" .-> OP["Overpass（公開サーバー）"]
```

## 方針・決定事項

| No. | 論点 | 決定 |
|---|---|---|
| ST-1 | 接続先 | 配信の基点の URL はアプリに組み込む。デバッグ版は開発用の配信を使う。リリース版は本番用の配信を使い、本番用ができるまでは開発用を使う。アプリ利用者には設定させない |
| ST-2 | 層 | `base`（スポット・施設・信号）と `shops`（お店）。どちらも同じタイルの番号で置く |
| ST-3 | 範囲 | Geofabrik の日本の抽出の範囲（`japan.poly`）に少しでもかかるタイルは、中身が空でもすべて置く。範囲にかからないタイルは置かない（404） |
| ST-4 | 新しさ | 週に 1 回作り直す。作り直したタイルは、遅くとも 1 時間で配信に反映される |
| ST-5 | 取り直し | URL は版を含まない。中身（時刻を除く）が変わったタイルだけを置き直すので、それ以外のタイルの `ETag` は変わらない。アプリは残っているタイルの `ETag` を `If-None-Match` で送り、変わっていなければ 304 を受ける |
| ST-6 | 圧縮 | タイルは gzip で置き、`Content-Encoding: gzip` で返す。アプリの HTTP クライアントが展開する |
| ST-7 | 送るもの | アプリが送るのは、URL（層とタイルの番号）・`If-None-Match`・アプリ名と版の `User-Agent` だけ。クエリ文字列・インストール ID・位置は送らない。配信はアクセスログを残さない |
| ST-8 | ライセンス | 各タイルに `license`（`ODbL-1.0`）と `attribution`（`© OpenStreetMap contributors`）を入れる。基点に `LICENSE.txt`（ODbL 1.0 で配っていること、出典、もとのデータの取得元と日時）を置く |
| ST-9 | 形式の置き場所 | タイルを読むのはアプリだけなので、読み込みは `kit-geo` に置き、core-format には置かない。機械可読な形は JSON Schema [`api/spot-tile.schema.json`](../api/spot-tile.schema.json) とし、バッチは書き出したタイルをこれで確かめる |

```mermaid
sequenceDiagram
    participant A as アプリ（kit-geo）
    participant C as 端末のタイル
    participant D as 配信（CloudFront）
    participant O as Overpass（予備）
    A->>A: 検索の中心から 600m の円にかかるタイル（1〜4 枚）
    A->>C: 残っているか・新しいか
    alt 残っていて新しい
        C-->>A: タイル
    else 残っていないか古い
        A->>D: GET /v1/base/xn76u.json（If-None-Match）
        alt 200 / 304
            D-->>A: タイル / 変わりなし
            A->>C: 残す
        else 404（範囲外）・取れない
            A->>O: 今と同じ検索
        end
    end
    A->>A: 今と同じ距離に絞って NearbyResult にする
```

## エンドポイント一覧

基点の URL を `{base}` と書く（例: `https://tiles.dev.example/`。実際の値は yorisoi-console の文書）。

| メソッド | パス | 内容 |
|---|---|---|
| GET | `{base}/v1/base/{geohash}.json` | 基本の層 |
| GET | `{base}/v1/shops/{geohash}.json` | お店の層 |
| GET | `{base}/LICENSE.txt` | ライセンスと出典 |

`{geohash}` は geohash の 5 桁（小文字、`0-9b-hjkmnp-z`）。

## リクエスト・レスポンス仕様

### URL と層

アプリは、チャンネルが店の種類を探すとき（`searchedKinds` が空でないとき）だけ `shops` を取る。`base` はいつも取る。

### HTTP の応答

| 状態 | 意味 | アプリの動き |
|---|---|---|
| 200 | タイル。`ETag`・`Content-Encoding: gzip`・`Content-Type: application/json` | 使い、`ETag` と取った時刻と一緒に残す |
| 304 | `If-None-Match` のタイルから変わっていない | 残っているタイルを使い、取った時刻を新しくする |
| 404 | 範囲外のタイル（ST-3） | 残っているそのタイルを消す。その検索は Overpass に問い合わせる |
| その他（5xx・通信の失敗・10 秒の時間切れ） | 取れない | 残っていれば古くても（90 日まで）使う。なければ Overpass に問い合わせる |

配信は、範囲外のタイルに 404 を返す（403 を返さない）。

### JSON の形

1 つのタイルは 1 つの JSON オブジェクト。

共通の項目:

| 項目 | 型 | 内容 |
|---|---|---|
| `format` | 文字列 | `yorisoi-spot-tile` |
| `version` | 整数 | `1`。互換の変更（項目を足す）では上げない |
| `layer` | 文字列 | `base` か `shops` |
| `tile` | 文字列 | geohash の 5 桁 |
| `osmTimestamp` | 文字列 | タイルの中身を作った OSM データの時刻（ISO 8601、UTC）。中身が変わらない週は作り直さないので、中身が最後に変わった週のもの |
| `generatedAt` | 文字列 | タイルを作った時刻（ISO 8601、UTC）。`osmTimestamp` と同じく、中身が最後に変わった週のもの |
| `license` | 文字列 | `ODbL-1.0` |
| `attribution` | 文字列 | `© OpenStreetMap contributors` |

`base` の層:

| 項目 | 型 | 内容 |
|---|---|---|
| `spots` | 配列 | スポット（下の「要素」）。輪郭を持てる |
| `facilities` | 配列 | 施設（下の「施設」） |
| `signals` | 数値の配列 | 信号の位置を `[緯度, 経度, 緯度, 経度, …]` と並べたもの |

`shops` の層:

| 項目 | 型 | 内容 |
|---|---|---|
| `shops` | 配列 | お店（下の「要素」）。輪郭は持たない |

要素（スポット・お店）:

| 項目 | 型 | 内容 |
|---|---|---|
| `id` | 文字列 | `node/123`・`way/123`・`relation/123`（今の `Poi.id` と同じ形。話した記録・よく通る道の記録がそのまま使える） |
| `lat`・`lon` | 数値 | 位置（[タイルの単位と位置](#タイルの単位と位置)） |
| `tags` | オブジェクト | OSM のタグをすべて（文字列から文字列） |
| `shape` | オブジェクト | スポットだけ、輪郭があるとき。`area`（真偽。面か線か）と `lines`（線の配列。各線は `[緯度, 経度, …]`、2 点以上） |

施設:

| 項目 | 型 | 内容 |
|---|---|---|
| `id` | 文字列 | 要素と同じ形 |
| `kind` | 文字列 | `FacilityKind` の名前（`TOILETS` など。[抜き出しの条件](#抜き出しの条件)） |
| `lat`・`lon` | 数値 | 位置 |
| `name` | 文字列 | `name:ja`、なければ `name`。どちらもなければ項目を置かない |
| `publicTransport` | 真偽 | `SHELTER` で `shelter_type=public_transport` のとき `true`。それ以外は項目を置かない |

例（`base`、一部）:

```json
{
  "format": "yorisoi-spot-tile",
  "version": 1,
  "layer": "base",
  "tile": "xn70z",
  "osmTimestamp": "2026-10-28T20:21:03Z",
  "generatedAt": "2026-10-29T03:10:44Z",
  "license": "ODbL-1.0",
  "attribution": "© OpenStreetMap contributors",
  "spots": [
    {
      "id": "way/123456",
      "lat": 35.31902,
      "lon": 139.55066,
      "tags": { "name": "鶴岡八幡宮", "amenity": "place_of_worship", "religion": "shinto" },
      "shape": { "area": true, "lines": [[35.3201, 139.5492, 35.3203, 139.5521, 35.3179, 139.5523, 35.3201, 139.5492]] }
    }
  ],
  "facilities": [
    { "id": "node/987", "kind": "TOILETS", "lat": 35.31871, "lon": 139.55102 },
    { "id": "node/988", "kind": "SHELTER", "lat": 35.31702, "lon": 139.55311, "name": "八幡宮前", "publicTransport": true }
  ],
  "signals": [35.31655, 139.55012, 35.31591, 139.54977]
}
```

## タイルの単位と位置

- 位置: ノードはその座標。ウェイとリレーションは、外接する四角形の中心（Overpass の `center` と同じ）。小数 5 桁に丸める
- タイル: 位置が入る geohash 5 桁のタイルに入れる。輪郭がタイルの外に出ていても、中心があるタイルに丸ごと入れ、ほかのタイルには入れない
- 輪郭: ウェイは自分の形。リレーションはメンバーのウェイごとの線（組み立てた輪でもよい。アプリは線の集まりとして扱う）。Douglas-Peucker で 5m に間引き、小数 5 桁に丸める
- 面か線か（`area`）: ウェイは、閉じていて `area` が `no` でなければ面。リレーションは、`type` が `multipolygon` か `boundary` なら面。今の `OverpassClient.shapeOf` と同じ
- 同じ場所がノードと面の両方にあるとき、施設が建物とノードの両方にあるときの重複は、バッチでは除かない。アプリが今と同じ規則で除く

## 抜き出しの条件

今の `OverpassClient.query` と同じ条件で抜き出す。件数の上限はかけない。条件を変えるときは、`OverpassClient` とバッチを同時に変え、本書を改める。

スポット（`base` の `spots`。`name` があるもの）:

| 条件 |
|---|
| `historic` がある |
| `tourism` が `attraction`・`museum`・`viewpoint`・`artwork`・`gallery` |
| `amenity=place_of_worship` |
| `leisure` が `park`・`garden` |
| `natural` が `tree`・`peak`・`spring`・`water`・`beach` |

施設（`base` の `facilities`）。上から順に、最初に合う種類にする:

| `kind` | 条件 |
|---|---|
| `TOILETS` | `amenity=toilets`、`access` が `private`・`no` でない |
| `DRINKING_WATER` | `amenity=drinking_water`、`access` が `private`・`no` でない |
| `VENDING_MACHINE` | `amenity=vending_machine`、`vending` が `ticket`・`parking`・`cigarette`・`excrement`・`condom`・`newspaper`・`fuel`・`photo` を含まない |
| `SHELTER` | `amenity=shelter`、`access` が `private`・`no` でない |
| `BENCH` | `amenity=bench` |
| `BICYCLE_PARKING` | `amenity=bicycle_parking`、`access` が `private`・`no` でない |
| `CONVENIENCE_STORE` | `shop=convenience` |
| `BICYCLE_SHOP` | `shop=bicycle` |
| `BICYCLE_PUMP` | `amenity=bicycle_repair_station` で `service:bicycle:pump=yes`、または `amenity=compressed_air` で `access` が `private`・`no` でない |

施設の条件に合う要素は、スポットやお店に入れない。

信号（`base` の `signals`）: ノードで `highway=traffic_signals`。

お店（`shops` の `shops`。`name` があり、`brand` も `brand:wikidata` もないもの）:

| 店の種類（API-CHANNEL-PACKAGE） | 条件 |
|---|---|
| `restaurant` | `amenity=restaurant` |
| `cafe` | `amenity` が `cafe`・`ice_cream` |
| `market` | `amenity=marketplace` |
| `sweets` | `shop` が `confectionery`・`pastry`・`bakery`・`chocolate` |
| `food_shop` | `shop` が `deli`・`tea`・`coffee`・`alcohol`・`wine`・`seafood`・`butcher`・`greengrocer`・`cheese`・`spices`・`tofu`・`rice` |
| `crafts` | `shop` が `craft`・`gift`・`antiques`・`art` |
| `shop` | `shop` が `books`・`second_hand`・`stationery`・`fabric`・`clothes`・`shoes`・`bag`・`jewelry`・`leather`・`musical_instrument`・`music`・`toys`・`florist`・`kitchen`・`interior_decoration` |

スポットの条件とお店の条件の両方に合う要素は、スポットに入れる。

## アプリの取り方

| No. | 決まり |
|---|---|
| AP-1 | 検索の中心から 600m の円にかかるタイル（1〜4 枚）を取る。同時に取ってよい |
| AP-2 | 取ったタイルを合わせ、今と同じ距離で絞る: スポット 600m、`near` の施設（自販機・ベンチ）250m、そのほかの施設 600m、お店 400m、信号 600m。そのうえで今の `OverpassClient.parse` と同じ規則で重複を除き、種類を決め、`NearbyResult` にする |
| AP-3 | お店はチャンネルの店の種類（`searchedKinds`）に合うものだけ、施設はアプリが探す種類（`facilityKinds`）だけ、信号は `signals` を求めるアプリだけ使う |
| AP-4 | 残っているタイルは、取った時刻から設定の期間（`freshMs`。既定 7 日）までは取り直さずに使う。過ぎたら `If-None-Match` で取り直す。取れなければ 90 日まで使う。90 日を過ぎたタイルは消す |
| AP-5 | 残すかどうか・期間・消すは、今の設定（スポットの答えを残す）に従う。残さない設定では、取ったタイルを端末に保存せず、アプリが動いている間だけメモリに置く |
| AP-6 | よく通る道の先読み（REQ-FREQUENT-WAYS F-6）は、道から 600m 以内にかかるタイルのうち、新しいものが残っていないタイルを取る。間を空けなくてよい |
| AP-7 | 1 つでもタイルが使えないとき（AP-1 の円にかかるタイルのうち、取れず残ってもいないものがあるとき）は、その検索は今と同じく Overpass に問い合わせる |
| AP-8 | 知らない項目・知らない `kind` は無視する。`format` が違う、`version` が 1 でない、JSON として読めないタイルは、取れなかったものとして扱う |

## エラー表

| 場面 | アプリの動き |
|---|---|
| 404 | 範囲外。残っているそのタイルを消し、Overpass に問い合わせる |
| 5xx・通信の失敗・時間切れ | 残っているタイルを使う。なければ Overpass |
| 読めないタイル（AP-8） | 取れなかったものとして扱い、残さない |
| Overpass も失敗 | 今と同じ（待ってから問い合わせ直す、近くの残っている答えを使う） |

## 確認観点

| No. | 観点 |
|---|---|
| CK-1 | 同じ地点で、タイルから作った `NearbyResult` と Overpass の答えが、上限を除いて同じ種類・件数になる（スポット・施設・お店・信号） |
| CK-2 | タイルの境目の近くで、隣のタイルの要素も距離の中に入る |
| CK-3 | 304 で残っているタイルを使い、中身を送られない |
| CK-4 | 404 のタイルでは Overpass に問い合わせ、残っているそのタイルを消す |
| CK-5 | 配信が止まっているとき、残っているタイル、なければ Overpass で今と同じに動く |
| CK-6 | `searchedKinds` が空のチャンネルでは `shops` を取らない |
| CK-7 | バッチの書き出したタイルが `spot-tile.schema.json` に合う |
| CK-8 | アプリの要求にクエリ文字列・インストール ID・位置が入らない |

## 互換性方針・移行メモ

- パスは `/v1`、タイルの `version` は `1`。項目を足すこと、施設の `kind` を足すこと（`FacilityKind` に種類を足すとき）は互換の変更とし、アプリは知らないものを無視する
- 抜き出しの条件を広げること（スポットやお店の種類を足す）は互換の変更とする。アプリは自分が知る条件で分け、合わない要素を使わない
- 項目の削除・意味の変更・タイルの単位の変更は `/v2` で行い、`/v1` は古いアプリが残る間置き続ける（期間は運営が決める）
- 移行: `kit-geo` がタイルを読めるようになるまでは、アプリは今のまま Overpass を使う。タイルを読めるアプリでも、タイルが取れなければ Overpass に戻るので、配信を作る前に出してよい

## 制約

- バッチは公開の Overpass を使わない（API-SPOT-TILES）
- 配信はアクセスログを残さない（ST-7）
- 抜き出しの条件は `OverpassClient.query` と同じに保ち、チャンネルの店の種類（API-CHANNEL-PACKAGE）と `FacilityKind` に従う。種類が増えたらバッチも合わせる
- 運営の担当者は 1 人（CHANNELS C-10）。運用は警報の知らせと、前の週のタイルを残すことで回す

## 見直しの条件

- 1 枚のタイルが大きすぎる地域が出たとき（都心で gzip 後 500KB を超えるなど。geohash 6 桁に細かくするか、層を分ける）
- 1 週間の遅れが散策の案内で問題になったとき
- Geofabrik の抽出が使えなくなったとき
- 日本の外でも使うことになったとき

## 未決事項・リスク

| No. | 内容 | 決める時期 |
|---|---|---|
| 2 | 本番の配信を作る時期 | 本番に出す前 |

リスク:
- 端末で 1〜4 枚を合わせて絞るので、都心では 1 回の検索で数 MB の JSON を読むことがある（gzip 前で 1 枚 約 500KB）。読み込みの時間とメモリに気をつける
- 抜き出しの条件が `OverpassClient` とずれると、予備に戻ったときと答えが変わる（CK-1 で確かめる）
- 日本全体の pbf から輪郭を組み立てるには、数 GB のメモリが要る
- 配信が止まると、端末にタイルが残っていない地域では Overpass に戻るので、遅さと失敗が増える

## 関連ドキュメント・参照リンク

- [API-SPOT-TILES](../contracts/API-SPOT-TILES.md)
- [API-CHANNEL-PACKAGE](API-CHANNEL-PACKAGE.md)（店の種類）
- [REQ-FREQUENT-WAYS](../requirements/REQ-FREQUENT-WAYS.md)（F-6）
- [`api/spot-tile.schema.json`](../api/spot-tile.schema.json)
- [geohash](https://en.wikipedia.org/wiki/Geohash)
- [OpenStreetMap の著作権とライセンス](https://www.openstreetmap.org/copyright)
