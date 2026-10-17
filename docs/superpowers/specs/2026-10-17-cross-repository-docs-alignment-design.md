# yorisoi 関連リポジトリの文書整合設計

## 状態

設計セクション 1〜3 はユーザーレビューで承認済み（2026-10-17）。書面設計のレビュー待ち。

## 目的

`yorisoi-platform` の文書体系再設計に合わせ、`yorisoi-sanpoguide`、`yorisoi-console`、`yorisoi-tutorial` の文書入口、正本の関係、用語、相互リンクを整える。各リポジトリの役割は異なるため、全 repo に同じフォルダー構成を強制せず、それぞれの読者と文書の性格に合った構成を保つ。

## 現状と問題

- `yorisoi-sanpoguide` はアプリ固有文書を `docs/` に持つが、`docs/README.md` がなく、ルート README は旧 platform 手引きパスを案内している。
- `yorisoi-console` は `docs/adr/`、`docs/design/`、`docs/guide/`、`docs/operations/` に分類済みだが、`docs/README.md` がない。ルート README、構成図、ガイド、設計書には移行前の platform 文書パスがある。
- `yorisoi-console` 独自の `ADR-001`、`DES-*`、管理 API・データモデルの番号は platform の ADR/API 番号と重なるため、リポジトリ名を省略すると参照が曖昧になる。
- `yorisoi-tutorial` は 86 の Markdown ファイルからなる独立した学習順を持つ。README、リポジトリ案内、保守手順に旧パス・旧用語・旧ファイル位置があり、教材と正本の関係も明確化が必要。
- tutorial の保守手順は複数 repo の `main` を追跡するため、canonical 文書の移動後も参照先を保守できる説明が必要。

## 合意した設計

### 1. リポジトリ別の情報構成

| リポジトリ | 文書の役割 | 変更方針 |
|---|---|---|
| `yorisoi-platform` | エコシステム共通の方針・契約・用語と SanpoGuide 固有の手引き | canonical な正本。2026-10-17 の文書再設計で移行済み |
| `yorisoi-sanpoguide` | Android アプリの動作・設計・公開・プライバシー・アプリ作業 | 既存ファイルを保ち、`docs/README.md` を追加。共通契約は platform の正本へ案内 |
| `yorisoi-console` | チャンネル管理システム固有の ADR・設計・API・データ・ガイド・運用 | 既存の分類と ID を保ち、`docs/README.md` を追加。共通契約との境界を明記 |
| `yorisoi-tutorial` | 実コードを教材にしたカリキュラム | 章立て・学習順を維持。入口、リポジトリ案内、保守手順、platform 参照を更新 |

`yorisoi-sanpoguide` の入口では、構造図・チャンネル・プロンプト、リリースとストア申告、プライバシー、アプリ固有 TODO を読者別に案内する。ルート README の手引きリンクを `yorisoi-platform/docs/apps/sanpoguide/manual/README.md` に更新する。

`yorisoi-console/docs/README.md` では、構成図、console の ADR/DES/API/データモデル、利用ガイド、運用手順、console 固有 TODO を案内する。共通の API 形式・エコシステム判断は platform の正本に従い、console の文書はその管理システム内での実装・運用を説明する。

`yorisoi-tutorial` は既存 README を入口として使い続ける。チュートリアルは説明資料であり、決定・契約・現行動作の正本ではないことを明示する。コードや契約が変わったときは、保守手順から canonical 文書と実装にたどって教材を更新できるようにする。

### 2. 正本、用語、文書 ID

文書間の権威を次のように区別する。

1. `yorisoi-platform` の承認済み ADR と契約: エコシステム共通の方針とシステム境界
2. `yorisoi-console` の ADR/DES/API/DM: 管理システム固有の設計と実装契約
3. `yorisoi-sanpoguide` の文書: アプリの利用・実装・運用・公開に関する記述
4. `yorisoi-tutorial`: 上記を学ぶための説明。正本と矛盾した場合は正本に合わせる

platform 文書のリンク先は、次の canonical パスを使う。

| 種別 | canonical パス |
|---|---|
| ADR | `docs/decisions/ADR-CHANNEL-CURATION.md`、`ADR-MATERIAL-PRODUCERS.md`、`ADR-ECOSYSTEM-PLATFORM.md`、`ADR-AI-ROLES.md` |
| API 契約 | `docs/contracts/API-SECRET-MESSAGES.md`、`API-CHANNEL-LIST.md`、`API-CHANNEL-PACKAGE.md`、`API-MATERIAL-PRODUCER.md` |
| 用語・役割 | `docs/overview/roles-and-vocabulary.md` |
| SanpoGuide 手引き | `docs/apps/sanpoguide/manual/README.md` |

platform の旧 ID `ADR-001`〜`ADR-004`、`API-001`〜`API-004` は互換参照として維持するが、他 repo の説明文では新しい canonical 文書タイトル・パスへ案内する。コードコメントとトレーサビリティの識別子自体は変更しない。

console の `ADR-001`、`DES-001`〜`DES-011`、console 管理 API・データモデルの ID はそのままにし、platform の同番号と並ぶ箇所では `console ADR-001`、`platform API-CHANNEL-LIST` のように所属を明記する。

共通の説明では「アプリ利用者」を用い、SanpoGuide の散策を指す記述では「散策者」を用いる。固有名詞・コード識別子（`publisher`、`producer`、`provider` 等）は変更しない。現況値や公開状態を更新する場合は、対応するコード・設定・運用情報で確認し、確認日を付す。法的意味や技術的合意は文書整理だけを理由に変えない。

### 3. リンクと既存構造

- 同一リポジトリ内のリンクは相対パスを用い、各 repo の Markdown で有効であることを検証する。
- sibling repository へのリンクは GitHub 上で解決できる canonical パスを用いる。チュートリアルの開発手順にあるローカル checkout のディレクトリ指定は、リンクとは分けて維持する。
- 移行済み platform 文書への旧 URL を canonical URL に更新する。移動・改名を新たに行わないため、SanpoGuide、console、tutorial 内には旧パス案内ページを追加しない。
- SanpoGuide の `docs/`、console の既存カテゴリ、tutorial の章番号・学習順は維持する。必要な入口の追加と文書内容・リンク更新にとどめる。

## 対象範囲

### 対象

- `yorisoi-sanpoguide/README.md` と `yorisoi-sanpoguide/docs/**/*.md`（計 8 文書）
- `yorisoi-console/README.md` と `yorisoi-console/docs/**/*.md`（計 26 文書）
- `yorisoi-tutorial/**/*.md`（計 86 文書）
- 対象文書内の用語、正本の案内、旧 platform パス、相互リンク、見出しアンカー、根拠を確認できる変動情報

### 対象外

- アプリ、console、platform のコード・API 仕様・OpenAPI YAML の変更
- `yorisoi-sanpoguide/prompts/` のプロンプトソースや生成物など、文書ではなく実行時データとして使われる Markdown
- tutorial のカリキュラムやハンズオン全体の書き直し・全実行
- 外部 URL が現在応答するかの検査
- 既存の console 番号、platform の互換 ID、コードコメントの変更

## 受け入れ条件

1. SanpoGuide と console の docs 入口から、対象読者・文書の役割・関連する正本が分かる。
2. tutorial は既存の学習順を保ち、canonical 文書と実装を参照しながら更新できる。
3. platform 共通の方針・契約と、SanpoGuide／console 固有の実装・運用情報を混同しない。
4. platform と console の番号を区別でき、旧 platform パスに依存する参照が残らない。
5. 現況や公開状態などの可変情報は、確認できる根拠と確認時点を伴う。確認できない場合は推測で書き換えず、未解決として提示する。
6. repo 内 Markdown の相対リンクと見出しアンカー、およびローカル checkout 上で確認できる repo 間参照を検査し、破損参照がない。
7. 変更は文書とそのリンクに限り、契約・動作・法的意味を暗黙に変更しない。

## 検証

- 各 repo の `README.md` と docs 入口からの案内を確認する。
- 対象 Markdown の旧 platform パス、曖昧な番号表記、対象境界を検索し、残存理由のある識別子とリンク切れを区別する。
- Windows 環境で実行できる一時検査で repo 内相対パスと見出しアンカーを検証する。
- sibling repository へのリンクは、リンクの canonical GitHub パスとローカル checkout の対応先を照合する。外部ネットワークへの疎通確認はしない。
- 変更した現況記述は、根拠となるコード・設定・対象文書と照合する。
- 文書のみの変更なので、ビルドやアプリテストは行わない。

## 設計上の判断

- **役割に合わせた構造を選ぶ。** 同一ツリーへの強制移行は、既に自然な分類を持つ console と、章順自体が UI である tutorial に移行コストとリンク切れを増やす。
- **入口を足し、既存 ID と章順を維持する。** 読者が探しやすくなる一方、外部参照・コード内トレーサビリティ・教材の順序を壊さない。
- **canonical な platform 文書へ直接案内する。** platform 側には旧パスの互換案内があるが、関連 repo の読者を案内ページ経由にせず正本へ導く。
- **教材を規範文書から分ける。** tutorial はコードを学ぶための有用な説明だが、設計判断や API の正本を複製しない。
