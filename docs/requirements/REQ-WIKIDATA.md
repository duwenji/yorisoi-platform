# スポットの Wikidata の情報 要件定義

## 文書情報
| 項目 | 内容 |
|---|---|
| 文書ID | REQ-WIKIDATA |
| ドキュメント種別 | 要件定義書 |
| 対象システム/機能 | スポットに結び付いた Wikidata の項目の情報を、スポットのタイルに入れて、解説シートの表示・AI の手がかり・写真・名の知れたスポットの判定に使う。platform kit-geo・API-SPOT-TILES、yorisoi-console（スポットのタイルのバッチ）、yorisoi-sanpoguide、yorisoi-cycleguide |
| 承認者 | 開発者（duwenji） |
| ステータス | 草案 |

## 目的・背景

スポットの情報（kit-geo `SpotFacts`）と、解説の AI に渡す手がかりは、OpenStreetMap（OSM）のタグだけから作っています。OSM のタグが少ないスポットでは、解説シートの行が少なく、AI も名前と種類だけで話すことになります。

OSM のスポットの多くには `wikidata` タグ（Wikidata の項目の ID）が付いています。しかし今は、その ID を次の 3 つにしか使っていません。

1. 写真: 端末がスポットごとに Wikidata に問い合わせ、画像（P18）のファイル名だけを取る
2. 名の知れたスポットの判定: タグがあるかどうかだけを見る（`Poi.notability`、CycleGuide の `SpotScale.MAJOR`）
3. AI への受け渡し: `wikidata=Q1146088` を値のまま渡す。AI には意味のない文字列

Wikidata の項目には、日本語の説明・読み・創建の年・文化財の指定・設計者・日本語版 Wikipedia の記事などがあります。Wikidata のデータは CC0 で、出典を示す義務がなく、ODbL のタイルに入れても扱いやすいものです。本書は、これらを運営のスポットのタイルに前もって入れ、両アプリで使う要件です。

## スコープ・非スコープ

### スコープ
- スポットのタイルのバッチが、スポットの Wikidata の項目から決めた値を取り、タイルに入れる（yorisoi-console `tiles/`、API-SPOT-TILES の変更）
- kit-geo: タイルの値の読み込み、`SpotFacts` での OSM のタグとの合わせ方、名の知れたスポットの判定の見直し、写真の取り方
- 両アプリ: 解説シートの表示と AI の手がかり（`SpotFacts` を通す）、名の知れたスポットの判定の追従、記録に残す値
- 文書の追従: プライバシーポリシー・利用者の手引き・出典の表示・技術チュートリアル

### 非スコープ
- Wikipedia の本文（要約）を AI の材料や画面に使うこと。本文は CC BY-SA で、出典の表示と、AI が話した内容が二次的著作物にあたるかの整理が要る（U-1）
- Overpass に問い合わせる予備の経路（API-SPOT-TILES S-9）で Wikidata の項目の値を取ること（D-2）
- お店（`shops` の層）の Wikidata の項目
- 日本語以外の言語のラベル・説明
- Wikidata の値をアプリ利用者・チャンネル提供者が直すこと（直すのは Wikidata と OSM の上で）
- iOS

## 対応元ID（トレーサビリティ）
| 対応元ID | 内容 | 対応状況 |
|---|---|---|
| 開発者の要望（2026-11-15） | Wikidata 関連を対応したい | 本書 |
| 開発者の決定（2026-11-15） | 使い道は全般（解説シートの表示、AI の手がかり、写真の先回り、名の知れたスポットの判定の見直し） | F-10〜F-24 |
| 開発者の決定（2026-11-15） | Wikidata の値は、審査を通った「AI の根拠」（API-MATERIALS）ではなく、OSM のタグと同じ「スポットの地図データ」として AI に渡す | D-3、F-16〜F-18 |
| 開発者の決定（2026-11-15） | Wikipedia の本文は今回は外し、別に検討する | 非スコープ、U-1 |
| 開発者の決定（2026-11-15） | CycleGuide にも、`SpotFacts` と `Narrator` を通して同時に入れる | D-10、F-25〜F-27 |

## 方針・決定事項

| No. | 決定 | 理由 |
|---|---|---|
| D-1 | Wikidata の値は、運営のスポットのタイルのバッチが週 1 回取り、タイルのスポットに入れる。アプリは、タイルから取ったスポットについては Wikidata に問い合わせない | 端末からの通信が増えない。スポットごとに第三者へ届く情報（項目の ID）が、写真のための問い合わせも含めて減る。圏外でも端末に残ったタイルで使える。週 1 回の新しさで足りる（スポットの情報は週の単位では変わらない） |
| D-2 | Overpass に問い合わせる予備の経路では、Wikidata の項目の値を取らない。そのスポットは今と同じく OSM のタグだけで表示し、写真だけは今と同じく端末が項目の画像（P18）を取る | 予備の経路はタイルが取れないときだけ通るので、まれ。仕組みを 2 つ持たない。写真は今あるものを減らさない |
| D-3 | Wikidata の値は「スポットの地図データ」として扱う。`SpotFacts` に OSM のタグと合わせて入れ、AI には今の地図データの情報（`facts`）として渡す。審査を通った「AI の根拠」（API-MATERIALS）には入れない | 開発者の決定（2026-11-15）。OSM と同じく、誰でも直せる公開のデータで、運営もチャンネル提供者も審査していない |
| D-4 | OSM のタグを先にし、Wikidata の値は OSM にない行だけを埋める。同じ行で両方に値があれば OSM を使う | OSM のタグは、その場所を見て付けられたものが多い。表示がタイルと予備の経路で大きく変わらない |
| D-5 | 日本語だけを使う。日本語のラベル・説明がない値は入れない | `SpotFacts` が、日本語で読めない値（英語の説明など）を出さないのと同じ |
| D-6 | OSM の `wikidata` タグの結び付きが誤っている項目は使わない。項目の座標（P625）がスポットから 2 km より離れているもの、項目の種類（P31）が場所でないもの（人・会社・市区町村など、運営が決めた一覧）は、その項目を入れない | OSM では、寺に開山の人物の項目、建物にそれを持つ会社の項目、公園に市の項目が付いていることがある。そのまま使うと、解説がよそのものの話になる |
| D-7 | 名の知れたスポットの判定（`Poi.notability`）を、日本語版 Wikipedia の記事があるか、国の文化財の指定があるかで決め直す（F-20） | 今はタグがあるだけで +2 になる。Wikidata の項目は小さな社にも付いていることがあり、予告（REQ-NEARBY-TALK F-11）や CycleGuide の「名の知れたものだけ」に、知られていないスポットが混ざる |
| D-8 | タイルの形は API-SPOT-TILES の `/v1` のまま、スポットに任意の項目 `wikidata` を足す（互換の変更）。OSM のタグ（`tags`）には混ぜない | `tags` は OSM のタグをすべて、という今の約束を保つ。古いアプリは知らない項目を無視する（AP-8） |
| D-9 | Wikidata は CC0 なので出典を示す義務はないが、タイルの配信の `LICENSE.txt` と、アプリの出典の表示（設定画面）に Wikidata を書く | どこから来た情報かを利用者と使う人に示す。タイルのライセンス（ODbL 1.0）は変えない |
| D-10 | SanpoGuide と CycleGuide に同時に入れる | 開発者の決定（2026-11-15）。どちらも `SpotFacts` と `Poi.notability` を使っているので、kit-geo を直せば両方に及ぶ |
| D-11 | バッチは Wikidata の API（`wbgetentities`）で項目を取る。Wikidata の全体のダンプは使わない | 全体のダンプは圧縮しても 100 GB を超え、週 1 回のバッチで扱う大きさではない。要るのは日本の OSM のスポットに結び付いた項目だけ |

## 機能要件

### タイルに入れる値（API-SPOT-TILES の変更）
| ID | 要件 |
|---|---|
| F-1 | `base` の層のスポットに、任意の項目 `wikidata`（オブジェクト）を足す。スポットの OSM の `wikidata` タグが 1 つの項目の ID（`Q` と数字）で、その項目が取れて D-6 に当たらないときだけ置く |
| F-2 | `wikidata` の中身は下の表のとおり。値がない項目は置かない（空の文字列・空の配列を置かない）。ラベルは日本語のもの（D-5） |

| 項目 | 型 | もとにする Wikidata の値 | 内容 |
|---|---|---|---|
| `id` | 文字列 | 項目の ID | `Q1146088` の形。別の項目に統合されていれば統合先の ID |
| `description` | 文字列 | 日本語の説明 | 一言の説明 |
| `reading` | 文字列 | P1814（カナ表記） | 名前の読み |
| `kinds` | 文字列の配列 | P31（種類）のラベル | 最大 3 つ |
| `inception` | 文字列 | P571（創建・設立） | OSM の `start_date` と同じ形（年の精度は `1063`、月は `1924-03`、日は `1924-03-01`、世紀は `C12`、十年は `1920年代`）。紀元前・精度が百年より粗いものは置かない |
| `heritage` | 文字列の配列 | P1435（文化財の指定）のラベル | 例: `重要文化財`・`国宝`・`史跡` |
| `architects` | 文字列の配列 | P84（設計者）のラベル | |
| `founders` | 文字列の配列 | P112（創設者）のラベル | |
| `jawiki` | 文字列 | 日本語版 Wikipedia の記事名 | |
| `image` | 文字列 | P18（画像） | Wikimedia Commons のファイル名（`File:` を付けない） |
| `website` | 文字列 | P856（公式サイト） | `http`・`https` の URL だけ |

| ID | 要件 |
|---|---|
| F-3 | 配列の値・1 つしか使わない値（`inception`・`image`・`website`）は、Wikidata の「優先」の値があればそれを、なければ「通常」の値を使う。「非推奨」の値は使わない。1 つしか使わない値は、そのうち最初のもの |
| F-4 | `id` 以外の値がひとつもない項目は、`wikidata` を置かない |
| F-5 | タイルの JSON Schema（`api/spot-tile.schema.json`）に `wikidata` を足し、バッチは書き出したタイルを今までどおりこれで確かめる |

### バッチ（yorisoi-console `tiles/`）
| ID | 要件 |
|---|---|
| F-6 | タイルを作るとき、スポットの `wikidata` タグから項目の ID を集め、`wbgetentities` で 50 件ずつ取る（ラベル・説明・主張・日本語版 Wikipedia の記事名。言語は日本語だけ）。値に出てくる項目（P31・P1435・P84・P112）のラベルも、同じ方法で取る |
| F-7 | Wikimedia の API の決まりに従う: 今のバッチと同じ `User-Agent`（バッチの名前とリポジトリの URL）を送り、要求は 1 つずつ順に送り、`maxlag=5` を付け、`Retry-After` や混雑の答えには待ってから送り直す |
| F-8 | 取った値は、配信しない場所（同じバケットの公開しない接頭辞）に前の週の分として残す。ある項目が今週取れなかったときは、前の週の値を使う。取れなかった項目が全体の 1 割を超えたら、Wikidata の値を差し替えずに前の週のものを使い、運営に知らせる（API-SPOT-TILES S-6 の知らせと同じ経路） |
| F-9 | D-6 の「場所でない種類」の一覧はバッチに持つ。最初の版は、人（Q5）・会社などの組織・市区町村など行政の区画・家族と氏族を入れる。一覧で外した項目の数をバッチの記録に出す |

### スポットの情報（kit-geo `SpotFacts`）
| ID | 要件 |
|---|---|
| F-10 | `Poi` に、タイルの `wikidata` を読んだ値（`SpotWikidata`、なければ null）と、タイルから取ったか（`fromTile`）を足す。予備の経路のスポットでは null と偽 |
| F-11 | `SpotFacts.of` は、OSM のタグと `SpotWikidata` を受け取る。各行は OSM の値を先に使い、なければ Wikidata の値で埋める（D-4）: 読み ← `reading`、説明 ← `description`、創建・設置 ← `inception`（今の `start_date` と同じ読み方）、文化財 ← OSM の `heritage:type`、なければ Wikidata の `heritage`、なければ OSM の `heritage` の段階（「国の指定」など）の順、設計 ← `architects` |
| F-12 | 行を 2 つ足す: 「創設者」（`founders`）と「種類」（`kinds` のうち、スポットの種類 `Poi.category` と同じ言葉でないもの）。行の並びは、説明・種類・創建・設置・時代・宗教・文化財・創設者・作者・設計・開いている時間・料金・碑文 |
| F-13 | 一行の要約（`summary`）の文化財は、F-11 で決めた文化財の値を使う |
| F-14 | リンク: Wikipedia は OSM の `wikipedia` タグを先にし、なければ `jawiki` から日本語版の記事のリンクを作る。公式サイトは OSM の `website`・`contact:website` を先にし、なければ `website` |
| F-15 | スポットを後のために残すアプリ（`SpotFacts.KEYS` のタグだけを残すもの）が `SpotWikidata` も残せるように、kit-geo に残す形（JSON への書き出しと読み込み）を置く |

### AI の手がかり（両アプリ）
| ID | 要件 |
|---|---|
| F-16 | スポットの解説の AI に渡す地図データの情報（`facts`）は、F-11・F-12 で合わせた `SpotFacts` から作る（今の `GuidePrompt.facts`・`Narrator.facts` のまま、中身が増える） |
| F-17 | AI に渡す OSM のタグの一覧（`USEFUL_TAGS`）から、`wikidata` と `wikipedia` を外す。ID や記事名を値のまま渡しても AI には意味がなく、AI が記事を読んだかのように話すもとになる |
| F-18 | プロンプトの固定部分の地図データの情報の説明を「OpenStreetMap と Wikidata の地図データ。誰でも直せるデータで、誤りがありうる」と改める。地図データの情報の扱い（事実として話してよい範囲）は今のまま変えない。両アプリのプロンプトの版を上げ、正解ファイルと審査用の見本に、Wikidata の値だけがあるスポット（OSM のタグが名前と種類だけ）を 1 つ足す |

### 写真（kit-geo `SpotPhotos`。SanpoGuide）
| ID | 要件 |
|---|---|
| F-19 | 写真のファイル名は、OSM のタグ（`wikimedia_commons`・`image`）→ `SpotWikidata.image` の順に探す。タイルから取ったスポット（F-10 の `fromTile`）では Wikidata に問い合わせず、どちらもなければ写真なしにする（D-6 で外した項目の画像を出さないため）。予備の経路のスポットは、今と同じく `wikidata` タグの項目の画像（P18）を問い合わせる |

### 名の知れたスポットの判定（kit-geo `Poi.notability`）
| ID | 要件 |
|---|---|
| F-20 | `notability` を次の合計にする: ① 日本語版 Wikipedia の記事がある（OSM の `wikipedia` タグが `ja:` で始まる、または `SpotWikidata.jawiki`）なら 2、それ以外で `wikipedia` か `wikidata` のタグがあれば 1 ② 文化財の指定がある（OSM の `heritage` が `1`・`2`、または `SpotWikidata.heritage` に国宝・重要文化財・史跡・特別史跡・名勝・特別名勝・天然記念物・特別天然記念物・世界遺産の一覧のどれかがある）なら 1 ③ 主要カテゴリなら 1（今のまま） |
| F-21 | 予告の目安（SanpoGuide `TalkDecider.PREVIEW_NOTABILITY`、2）は変えない。①の改めで、日本語版 Wikipedia の記事がなく、文化財でも主要カテゴリでもないスポットは予告しなくなる |
| F-22 | 周りの見どころの選び方（`NearbyHighlights`）・SanpoGuide の 1 件の選び方（`SpotChooser`）・CycleGuide の前方の見どころの選び方は、`notability` を使ったまま変えない（F-20 の値の変化が及ぶ） |
| F-23 | CycleGuide の「名の知れたものだけ」（`SpotScale.MAJOR`）は、`notability` が 2 以上、または今の観光の種類（`attraction`・`museum`・`viewpoint`）のスポットとする。タグがあるだけでは名の知れたものとしない |
| F-24 | 予備の経路のスポットでは ① を OSM のタグだけで決める。同じスポットでもタイルと予備で値が 1 違うことがあるが、許す（R-3） |

### CycleGuide
| ID | 要件 |
|---|---|
| F-25 | `Narrator.facts` と `USEFUL_TAGS` を F-16・F-17 のとおりにする |
| F-26 | 走行の履歴（`RideHistory`）に残すスポットに、`SpotWikidata` も残す（F-15）。前の版で残した履歴は、`SpotWikidata` なしで読める |
| F-27 | 走行の振り返り・前回の走行の地図で出すスポットの情報も、残した `SpotWikidata` を使う |

### 文書の追従
| ID | 要件 |
|---|---|
| F-28 | 両アプリのプライバシーポリシー・利用者の手引き・README の、Wikimedia に送るものの説明を改める: Wikidata の値は運営のスポットのタイルから来ること、端末が Wikidata に問い合わせるのはタイルが取れなかったスポットの写真だけであること |
| F-29 | 両アプリの設定画面の出典に Wikidata（CC0）を足し、タイルの配信の `LICENSE.txt` に Wikidata から足した値があることを書く（D-9） |
| F-30 | 技術チュートリアル（yorisoi-tutorial）の、スポットのタイル・`SpotFacts`・名の知れたスポットの説明を追従する |

## 非機能要件

| ID | 要件 |
|---|---|
| N-1 | プライバシー: アプリが外へ送るものは増えない。タイルのスポットについては、Wikidata への問い合わせ（項目の ID）がなくなる |
| N-2 | 大きさ: タイルの合計の大きさ（gzip 後）の増え方は 1 割以内を目安にする。超えたら `kinds`・`founders` から削る |
| N-3 | バッチの時間: Wikidata を取る時間は、今のバッチに 1 時間以内で足りること |
| N-4 | 性能: タイルを読む時間・メモリは、今と比べて目に見えて増えないこと（都心のタイル 4 枚で確かめる） |
| N-5 | 新しさ: Wikidata の値は週 1 回の作り直しで新しくなる。タイルの中身（時刻を除く）が変わるので、値が変わったタイルだけ `ETag` が変わる（API-SPOT-TILES ST-5 のまま） |

## 受入条件

1. AC-1（F-1〜F-5）: 作ったタイルが Schema に合い、`wikidata` タグのある鎌倉・京都のよく知られたスポットに `wikidata` があり、`jawiki`・`heritage`・`image` が入っている
2. AC-2（D-6、F-9）: 開山の人物・持つ会社・市の項目が付いたスポットに、`wikidata` が置かれない（バッチのテスト）
3. AC-3（F-8）: Wikidata の API が答えないとき、前の週の値でタイルができ、運営に知らせが届く
4. AC-4（F-11〜F-14）: OSM のタグが名前と種類だけで、`wikidata` のあるスポットの解説シートに、説明・創建・文化財・Wikipedia のリンクが出る。OSM にも値がある行では OSM の値が出る（テスト）
5. AC-5（F-16〜F-18）: そのスポットの解説の AI に、合わせた地図データの情報が渡り、`wikidata=Q…` は渡らない。審査用の見本で、AI が渡した値を言い換えずに話す
6. AC-6（F-19）: タイルのスポットの写真を出すとき、端末から `www.wikidata.org` への要求がない。予備の経路のスポットでは今と同じく写真が出る
7. AC-7（F-20〜F-23）: 日本語版 Wikipedia の記事がなく、文化財でも主要カテゴリでもない、`wikidata` タグだけのスポットが予告されず、CycleGuide の「名の知れたものだけ」でも話されない（テスト）
8. AC-8（F-26）: 前の版で残した CycleGuide の走行の履歴が読め、新しい走行の履歴では振り返りに Wikidata の値が出る
9. AC-9（N-2・N-3）: 日本全体のバッチで、タイルの合計の大きさの増え方と、Wikidata を取った時間・件数・外した件数を記録し、目安に収まる
10. AC-10: 実機で散歩・走行をし、解説シートと AI の話が、OSM だけのときより充実して聞こえ、よそのものの話が混ざらないと開発者が感じる

## 用語定義
| 用語 | 意味 |
|---|---|
| Wikidata | ウィキメディア財団が運営する、誰でも直せる構造化データの集まり。データは CC0 |
| 項目 | Wikidata の 1 つのもの（`Q` と数字の ID を持つ） |
| 主張・プロパティ | 項目の値（P571 創建など）。各値は「優先・通常・非推奨」の順位を持つ |
| `SpotWikidata` | タイルの `wikidata` を kit-geo で読んだ値 |
| 予備の経路 | タイルが取れないときに Overpass に問い合わせてスポットを取ること（API-SPOT-TILES S-9） |

## 制約
| 種類 | 内容 | 本書での扱い |
|---|---|---|
| Wikidata のライセンス | データは CC0。出典の表示は義務ではない | 出典を示す（D-9）。タイルのライセンス（ODbL）は変えない |
| Wikimedia の API の決まり | `User-Agent` で誰かを示す、並列に送らない、`maxlag` に従う | F-7 |
| Wikipedia のライセンス | 本文は CC BY-SA | 本文は使わない。記事名からリンクを作るだけ（F-14、U-1） |
| API-SPOT-TILES の互換性 | 項目を足すのは互換の変更 | `/v1` のまま `wikidata` を足す（D-8） |

## 未決事項・リスク
| ID | 内容 | 対応 |
|---|---|---|
| U-1 | Wikipedia の本文（要約）を AI の材料や画面に使うか。CC BY-SA の出典の表示と、AI の話が二次的著作物にあたるかの整理が要る | 別の要件で検討する |
| U-2 | 日本の OSM のスポットのうち、`wikidata` タグのある数（バッチの要求の回数と時間のもと） | X-1 で日本全体を流して数え、N-3 を確かめる |
| R-1 | Wikidata の値の誤り・荒らし（説明の書き換えなど） | 地図データとして扱い、事実の範囲を広げない（D-3）。週 1 回の取り直しで直る。目立つものは Wikidata 上で直す |
| R-2 | OSM の `wikidata` タグの結び付きの誤りで、よその項目の話になる | D-6 の確かめ。外した数をバッチの記録で見て、一覧を直す |
| R-3 | タイルと予備の経路で、同じスポットの `notability` や解説シートの行が違う。予備の経路では D-6 の確かめがないので、結び付きの誤った項目の写真が出ることがある（今と同じ） | 予備の経路はまれなので許す（F-24） |
| R-4 | Wikidata の API の利用の制限や止まりで、バッチが遅れる・値が古くなる | 前の週の値を使い、知らせる（F-8） |
| R-5 | F-20 の改めで、予告や CycleGuide の「名の知れたものだけ」で話すスポットが減りすぎる | 実機の記録で予告の回数を見る。減りすぎたら ① の「それ以外」を 1 から見直す |

## 作る順番
| No. | 段階 | 場所 |
|---|---|---|
| X-1 | API-SPOT-TILES と Schema の変更（F-1〜F-5）、バッチ（F-6〜F-9）。開発用に配備し、日本全体で流す（AC-1〜AC-3・AC-9） | platform、yorisoi-console |
| X-2 | kit-geo: `SpotWikidata`・タイルの読み込み・`SpotFacts`・`notability`・写真・残す形（F-10〜F-15・F-19〜F-24） | platform |
| X-3 | SanpoGuide: AI の手がかり・プロンプト・見本・出典・文書（F-16〜F-18・F-28・F-29） | yorisoi-sanpoguide、yorisoi-console（validator の追従） |
| X-4 | CycleGuide: 同じと履歴（F-16〜F-18・F-23・F-25〜F-29） | yorisoi-cycleguide、yorisoi-console（validator の追従） |
| X-5 | 技術チュートリアルの追従（F-30） | yorisoi-tutorial |
| X-6 | 実機での確認（AC-10） | 両アプリ |

## 関連ドキュメント・参照リンク
- [API-SPOT-TILES](../contracts/API-SPOT-TILES.md)（タイルの形、互換性、バッチ S-6）
- [REQ-NEARBY-TALK](REQ-NEARBY-TALK.md)（周りの見どころ、予告 F-11、地図データの情報を解説に渡す F-8）
- [API-MATERIALS](../contracts/API-MATERIALS.md)（AI の根拠）
- [AI-ROLES](../overview/ai-roles.md)
- [Wikidata:Data access](https://www.wikidata.org/wiki/Wikidata:Data_access)・[wbgetentities](https://www.wikidata.org/w/api.php?action=help&modules=wbgetentities)
- [Wikimedia の User-Agent の方針](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy)・[maxlag](https://www.mediawiki.org/wiki/Manual:Maxlag_parameter)
