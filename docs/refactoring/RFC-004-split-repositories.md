# RFC-004 リポジトリを分ける: 共通パッケージ（yorisoi-platform）とアプリごとのリポジトリ

## 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | RFC-004 |
| ドキュメント種別 | リファクタリング計画書 |
| 対象システム/機能 | SanpoGuide のリポジトリ全体（`platform/`・`apps/sanpoguide/`・`docs/`・CI と公開のワークフロー）。yorisoi-console・yorisoi-tutorial の参照先 |
| 関連Skill | refactoring-safety |
| 作成日 | 2026-10-13 |
| 作成者 | Claude（開発者との検討） |
| 承認者 | 開発者（1.0: 2026-10-13） |
| ステータス | 実施済（2026-10-13。手元のフォルダの整理を除く） |
| 版 | 1.1 |

## 目的・背景

[ADR-003](../adr/ADR-003-companion-app-platform.md) E-19 では、Kotlin 側を 1 つの Gradle モノレポ（このリポジトリ）にすると決めた。コアとアプリを同じ PR で直せるようにするためだった。

2026-10-13 に、開発者から次の方針が示された。

1. 共通パッケージ（コアとキット）を別のリポジトリにする
2. アプリごとにリポジトリを分ける

あわせて、次のことを開発者と決めた（2026-10-13）。

| 論点 | 決定 |
|---|---|
| 進め方 | ADR-003 を改訂し（1.3。E-19 を改め E-28 を足す）、本書の承認後に分ける |
| 共通パッケージのリポジトリ名 | `yorisoi-platform` |
| SanpoGuide のリポジトリ | 新しく作る（`yorisoi-sanpoguide`。未決事項 No.1）。今の `duwenji/SanpoGuide` は移行の後にアーカイブする（実際には、開発者の判断で削除した。R-9） |
| アプリが共通パッケージを使う方法 | GitHub Packages に公開した版を固定して使う。コアとアプリを同時に直すときだけ、手元の Gradle の設定で隣のフォルダのソースに差し替える（composite build） |

分ける理由（ADR-003 1.3 E-28 に記録する）:
- 2 本目のアプリ（サイクリングの伴走。E-23）からは、アプリごとに出す時期・権限・Play の設定が変わる。1 つのリポジトリでは、1 つのアプリの変更がほかのアプリの CI を壊すおそれがあった（ADR-003 のリスク）
- 共通パッケージを版で固定すると、アプリは自分の都合の良いときに上げられる。管理システム（yorisoi-console）はすでに版を固定して使っており、アプリも同じ関係になる
- 同じ PR で直せる利点は、手元の差し替え（composite build）で大部分を残せる

今のアプリは SanpoGuide だけなので、本書で作るリポジトリは 2 つ（`yorisoi-platform` と SanpoGuide のリポジトリ）。サイクリングのアプリ（ADR-003 T-9）は、最初から自分のリポジトリで作る。

**アプリの動きは変えない。** 分けたあとの APK の中身（クラスとリソース）が、分ける前と同じになることを確かめる。

## 対応元ID（トレーサビリティ）

| 対応元ID | 内容 | 対応状況 |
|---|---|---|
| ADR-003 E-19 | リポジトリの構成 | 1.3 で改める（本書の前提） |
| ADR-003 E-28（1.3 で追加） | リポジトリを分ける理由と関係 | 本書で手順にする |
| ADR-003 T-8 | 新しいアプリの雛形（`templates/`） | 置き場所を `yorisoi-platform` にする |
| ADR-003 T-11 | yorisoi への改名 | 本書の前提（PR #55 のマージ後に始める） |
| RFC-002 R-11 | 公開（コアとプロンプト・パック、キットは公開しない） | キットも公開するように改める（R-4） |
| RFC-002 R-13 | CI | リポジトリごとに分ける（R-6） |

## 方針・決定事項

| No. | 論点 | 決定（提案） |
|---|---|---|
| R-1 | 始める時期 | PR #55（yorisoi への改名）をマージしてから始める。#55 はパッケージと公開の座標を変えるので、先に分けると衝突が大きくなる。#55 のあとは、分け終わるまで SanpoGuide の `main` への変更を止める |
| R-2 | `yorisoi-platform` に移すもの | `platform/` のモジュール（`core-format`・`core-prompt`・`kit-voice`・`kit-llm`・`kit-geo`・`kit-mood`・`kit-companion`）、Gradle の wrapper と `libs.versions.toml`（写す）、エコシステム全体の文書（下の表）。今後の `templates/`（ADR-003 T-8） |
| R-3 | SanpoGuide のリポジトリに残すもの | `apps/sanpoguide/app`・`apps/sanpoguide/prompts`、アプリの文書（下の表）、署名の設定（`keystore.properties.example`）、`releases/`。モジュールはリポジトリの直下に上げる（`:apps:sanpoguide:app` → `:app`、`:apps:sanpoguide:prompts` → `:prompts`）。アプリだけのリポジトリで、深い階層の意味がなくなるため。`applicationId`・パッケージ名・リソースの中のパス（`sanpoguide/channels/…`）は変えない（`applicationId`・パッケージ名は、分割の後に ADR-003 1.4 で `io.github.duwenji.yorisoi.sanpoguide` に改めた） |
| R-4 | 公開 | `yorisoi-platform` は、コアに加えて**キットも** GitHub Packages に公開する（キットは AAR、`kit-companion` は JAR）。座標は `io.github.duwenji.yorisoi:<モジュール名>`。版は全モジュールで 1 つ（`yorisoiVersion`）、タグは `v<版>`。コアだけ・キットだけの版を分けると、組み合わせの確認が増えるため。最初の版は 1.0.0（新しい座標なので 1.0.0 から始める。開発者の判断、2026-10-13。yorisoi の座標ではまだ公開していない。タグは `michizure-core-v1.3.0` まで） |
| R-5 | プロンプト・パックの公開 | SanpoGuide のリポジトリから公開する（座標 `io.github.duwenji.sanpoguide:prompts` は変えない。タグ `prompts-v<版>`）。GitHub Packages では、パッケージが最初に公開したリポジトリに結びつくため、新しいリポジトリから 2.0.0 を出せるかを手順 1 で確かめる（未決事項 No.2） |
| R-6 | CI | リポジトリごとに持つ。`yorisoi-platform`: 全モジュールのテストと lint、公開の前にもう一度テスト。SanpoGuide: 公開された版を読んでテスト・lint・リリースビルド。GitHub Packages の Maven は公開のパッケージでも読むのにトークンが要る。`yorisoi-platform` は公開リポジトリなので `GITHUB_TOKEN` で読める（非公開のあいだは、各パッケージの「Actions のアクセス」に SanpoGuide のリポジトリを足す必要があった） |
| R-7 | 手元での差し替え | SanpoGuide の `settings.gradle.kts` は、Gradle のプロパティ `yorisoi.platformDir`（`local.properties` ではなく `~/.gradle/gradle.properties` か `-P`）があれば、そのフォルダを `includeBuild` する。`yorisoi-platform` のモジュールの `group` と名前が座標と同じなので、Gradle が公開の版をソースに自動で置き換える。CI では設定しない（常に公開の版で確かめる） |
| R-8 | 履歴 | `yorisoi-platform`: `git filter-repo` で、R-2 のパスの履歴だけを取り出して作る（`platform/` は 2026-10-12 に作ったので、それより前の履歴は `station-format/`・`app/` のパスも含めて取り出す）。SanpoGuide: 今のリポジトリの履歴をそのまま新しいリポジトリに push し、`platform/` を消すコミットを足す。タグは、`michizure-core-v*`・`station-format-v*` を `yorisoi-platform` に、`sanpoguide-prompts-v*` を SanpoGuide に持っていく |
| R-9 | 今の `duwenji/SanpoGuide` | 移行と確認のあとにアーカイブする（読むだけになる）。README の先頭に、新しい 2 つのリポジトリへの案内を書いてからアーカイブする。Issue・PR・過去の公開物（`station-format`・`michizure` の版）は残る。消さない。**2026-10-13 に開発者の判断で、アーカイブではなく削除した**（Issue・PR・Actions の記録と、リポジトリに結びついた公開物も一緒に消えた。使っているところはない） |
| R-10 | 他のリポジトリの参照 | yorisoi-console の validator が読むリポジトリ（`maven.pkg.github.com/duwenji/SanpoGuide`）を、コアは `yorisoi-platform`、プロンプト・パックは SanpoGuide の新しいリポジトリに変える。文書のリンクも直す。yorisoi-tutorial は、MAINTENANCE.md の照合先を 2 つのリポジトリにし、本文のパス（`platform/…`・`apps/sanpoguide/…`）を直す |

### 文書の置き場所

エコシステム全体に関わるもの（コア・管理システム・すべてのアプリが従う契約と決定）は `yorisoi-platform` に、SanpoGuide だけのものは SanpoGuide に置く。

| 文書 | 置き場所 | 理由 |
|---|---|---|
| ADR-003（エコシステム） | yorisoi-platform | エコシステムの全体の決定 |
| ADR-001（第三者のチャンネルの審査と署名）・ADR-002（素材提供者） | yorisoi-platform | ADR-003 でアプリ共通になった（E-8〜E-16） |
| API-002（channel-list-api）・API-003（channel-package-format）・API-004（material-producer-api） | yorisoi-platform | コアが実装し、管理システムとすべてのアプリが従う |
| channel-review-policy.md（草案） | yorisoi-platform | 審査はアプリをまたいで 1 つ（E-18） |
| stakeholders.md | yorisoi-platform | 呼び方はエコシステム共通（E-27）。SanpoGuide の呼び方（散策者）も同じ表に残す |
| RFC-002・RFC-003・本書 | yorisoi-platform | プラットフォームを作った記録 |
| RFC-001 | SanpoGuide | `station-format` を作った記録（SanpoGuide の中の出来事） |
| README・TODO.md・architecture.md（.html）・channels.md・prompts.md・secret-messages-api.md・`api/` | SanpoGuide | アプリの文書。architecture.md のプラットフォームの部分は、yorisoi-platform の README に要約して移し、リンクにする |
| `skill-logs/` | SanpoGuide（そのまま） | 当時の作業の記録。動かさない |

移した文書は、SanpoGuide 側にはリンクだけを残す（TODO.md の該当の行など）。移した文書の中の相対リンクで、もう一方のリポジトリを指すものは、GitHub の URL に直す。

## 手順と保証

| 手順 | 内容 | 保証（確かめること） |
|---|---|---|
| 0 | 分ける前の記録: PR #55 のマージ後の `main` で、`./gradlew test lint assembleRelease` が通ることと、APK の中のクラスとリソースの一覧（RFC-002 の `RFC-002-apk-files.txt` と同じ方法）とプロンプトの組み立て結果（`prompt-golden.txt`）を残す | — |
| 1 | `yorisoi-platform` を作る（非公開で作り、手順 4 のあとで公開にする）。R-8 の方法で履歴ごと取り出し、ルートの Gradle の設定・wrapper・`libs.versions.toml` を足す。全モジュールの公開の設定（R-4）、CI（R-6）、README。文書を移してリンクを直す | `./gradlew test lint` が通る。`publishToMavenLocal` で 7 モジュールが出る。タグ `v2.0.0` で GitHub Packages に公開できる |
| 2 | SanpoGuide の新しいリポジトリを作る（R-8）。`platform/` を消し、モジュールを直下に上げ（R-3）、依存を `project(":platform:…")` から公開の座標（`io.github.duwenji.yorisoi:…:1.0.0`）に変える。R-7 の差し替え、CI（R-6）、プロンプト・パックの公開（R-5） | `./gradlew test lint assembleRelease` が公開の版で通る。APK の一覧とプロンプトの組み立て結果が手順 0 と同じ（差はモジュールのパスから来るもの以外にない）。`-Pyorisoi.platformDir=../yorisoi-platform` でも同じものができる |
| 3 | エミュレータで散策の確認（RFC-002 と同じ: 地図・施設のチップ・散策モードの開始・話しかけ・チャンネルの切り替え・雰囲気の絵） | 手順 0 と同じに動く |
| 4 | 他のリポジトリの切り替え（R-10）: yorisoi-console の validator と文書、yorisoi-tutorial | 管理システムの CI が通る。開発用の環境で機械審査が動く |
| 5 | 今の `duwenji/SanpoGuide` に案内を書き、アーカイブする（R-9）。手元のフォルダを `c:\Dev\apps\yorisoi-platform`・`c:\Dev\apps\<SanpoGuide の新しい名前>` にする | 新しいリポジトリへのリンクが README から辿れる |

外に出る操作（リポジトリの作成・公開・アーカイブ・タグの push）は、手順ごとに開発者の確認を取ってから行う。

### 実施の記録

| 手順 | 日付 | 結果 |
|---|---|---|
| 0 | 2026-10-13 | #55 のマージ後の `main`（0f3d7d8）で `./gradlew test lint assembleRelease` が通った。テストは 460 件（`platform/` 159 件、アプリとプロンプト・パック 247 件。debug・release の両方を数えた件数）。リリース版 APK の中身（全 344 ファイルの CRC32）と、dex のクラスの一覧（47,723）を記録した |
| 1（手元） | 2026-10-13 | `git filter-repo` で、`platform/`（直下に上げた）・`station-format/`・移す文書の履歴を取り出した（94 コミット、タグは `michizure-core-v*`・`station-format-v*`）。R-8 の「`app/` のパスも含める」はやめた: `app/` 全体を取り出すとアプリの履歴まで入るため。キットの 2026-10-12 より前の履歴は yorisoi-sanpoguide に残る（README に記した）。公開の設定はルートの `build.gradle.kts` にまとめた（各モジュールは `description` だけ書く）。結果: `./gradlew test lint` が通り、テストは 159 件（手順 0 の `platform/` と同じ）。`publishToMavenLocal` で 7 モジュール（JAR 3・AAR 4）が出て、POM の依存はモジュールどうしが `io.github.duwenji.yorisoi:*:2.0.0` になった（このあと版を 1.0.0 に改めた）。移した文書からアプリの文書へのリンク 26 か所を yorisoi-sanpoguide の URL にした |
| 2（手元） | 2026-10-13 | 今の履歴をそのまま写し（タグは `sanpoguide-prompts-v*` だけ）、`platform/` と移した文書を消し、`:app`・`:prompts` に上げた。依存は `libs.versions.toml` の `yorisoi-*`（2.0.0。このあと 1.0.0 に改めた）。手元では mavenLocal から読んだ。結果: `./gradlew test lint assembleRelease` が通り、テストは 247 件（手順 0 と同じ）。APK はクラスの一覧と資源ファイルがすべて同じで、差は `version-control-info`（コミットの ID）・`baseline.prof`（1 バイト）・`classes5.dex` だけ。dex の差を逆アセンブルで比べ、`BuiltInChannels` のエラーの文言（`:apps:sanpoguide:prompts` → `:prompts`）だけだと確かめた。`-Pyorisoi.platformDir=../yorisoi-platform` で公開の版がソースに置き換わり、リリースビルドが通ることも確かめた。気づいたこと: モジュールを直下に上げたので、lint が手元の `local.properties` を見るようになり、`sdk.dir` の `:` のエスケープを求める（Android Studio が書く形 `C\:/…` にすれば通る。CI には `local.properties` がないので影響しない） |
| 1・2（GitHub） | 2026-10-13 | 開発者の判断で、最初の版を 2.0.0 から **1.0.0** に改めた（新しい座標なので）。`duwenji/yorisoi-platform`・`duwenji/yorisoi-sanpoguide` を非公開で作って push した。`yorisoi-platform` の最初の CI は `gradlew` に実行の権限がなく失敗した（Windows で写したため。`git update-index --chmod=+x` で直した）。タグ `v1.0.0` で 7 モジュールを公開した。`yorisoi-sanpoguide` の CI は、非公開のパッケージを読めず失敗した（`Could not find io.github.duwenji.yorisoi:core-format:1.0.0`）。R-6 の Actions access を 7 つ設定する代わりに、開発者の判断で `yorisoi-platform` を公開にした（手順 1 の「手順 4 のあとで公開にする」を早めた）。再実行で `yorisoi-sanpoguide` の CI が通った |
| 3 | 2026-10-13 | `yorisoi-sanpoguide` の debug 版（公開された yorisoi-platform 1.0.0 でビルド）を、エミュレータ（Pixel_10_Pro_XL、鎌倉駅で位置を指定、API キーなし）に新しく入れて確かめた。起動、地図（国土地理院のタイル）と現在地、スポットの検索（Overpass は主サーバーが 504・予備の 1 つが時間切れで、3 つ目の maps.mail.ru で取れた。コードは関係ない）、施設のチップと地図のマーカーの色分け（`kit-geo` の drawable）、スポットの簡易解説とルートの検索、読み上げ（オーディオフォーカスの取得と日本語の TTS）、散策モードの開始（常駐サービス）、雰囲気の絵（「秋の昼、晴れ、史跡の近く」）、話しかけのカード、組み込みの 6 チャンネルの一覧、歴史探訪への切り替えと切り替えのあいさつ、散策の終了。アプリのクラッシュはなかった |
| 4 | 2026-10-13 | 未決事項 No.2 が起きた: `yorisoi-sanpoguide` からプロンプト・パック 2.0.0 を公開すると 422（`io.github.duwenji.sanpoguide:prompts` は 1.x を公開した古い SanpoGuide に結びついている）。開発者の判断で古いパッケージ（1.0.0・1.1.0）を GitHub の画面で削除し、公開し直して成功した。yorisoi-console（公開リポジトリ）の CI が読めるよう、`yorisoi-sanpoguide` も開発者の判断で公開にした。yorisoi-console の validator を `core-format:1.0.0`（yorisoi-platform）・`prompts:2.0.0`（yorisoi-sanpoguide）に切り替え、文書のリンクを直した（yorisoi-console #38、CI 成功）。yorisoi-tutorial のリンク・構成の説明・ハンズオンを直した（yorisoi-tutorial #7） |
| 5 | 2026-10-13 | 開発者が GitHub の `duwenji/SanpoGuide` を削除した（アーカイブの予定を変えた）。削除の前に、使っているところがないことを確かめた: yorisoi-console は #38 で読み先を切り替え済み、yorisoi-tutorial は #7 で参照を切り替え済み。削除のあとに、yorisoi-console の画面（`web/src/pages/validation.tsx`）の宣伝の審査基準へのリンクが古いリポジトリを指していたのを見つけて直した（#39。#38 の置き換えが `.tsx` を対象にしていなかった）。履歴は yorisoi-sanpoguide（全部）と yorisoi-platform（`platform/`・`station-format/`・移した文書）に残っている |

### 戻し方

- 手順 1〜3 の間は、今の `duwenji/SanpoGuide` を何も変えていないので、新しいリポジトリを消せば元に戻る
- 手順 4 のあとは、yorisoi-console の参照を元のリポジトリに戻す PR で戻す
- 手順 5 のアーカイブは、GitHub の設定で解除できる（実際には削除したので、戻せない。GitHub は削除から 90 日以内ならリポジトリの復元を受け付ける）

## 影響範囲

| 観点 | 影響 |
|---|---|
| アプリ | 動きは変わらない。ビルドの依存が、モジュールから公開の版に変わる |
| 開発の流れ | コアとアプリを同時に直すときは、`yorisoi-platform` の PR を先にマージして公開し、アプリの PR で版を上げる。手元では R-7 の差し替えで一緒に試せる |
| 管理システム | 読むリポジトリの URL が変わる。版の固定は今のまま |
| チュートリアル | パスとリポジトリ名の書き直し（第 5 部が中心） |
| Play・散策者 | 影響なし（`applicationId` と署名の鍵は変わらない。`applicationId` は、Play に上げる前に ADR-003 1.4 で改めた） |
| 文書 | ADR-003 1.3、本書、移した文書のリンク、両リポジトリの README、TODO.md |

## 未決事項・リスク

| No. | 内容 | 対応 |
|---|---|---|
| 1 | ~~SanpoGuide の新しいリポジトリの名前~~ → `yorisoi-sanpoguide`（2026-10-13 承認）。提案は `yorisoi-sanpoguide`（エコシステムのリポジトリが `yorisoi-` でそろう）。ほかの案は、今のリポジトリを `SanpoGuide-archive` に改名してから `SanpoGuide` を作り直す（名前を保てるが、改名の転送が新しいリポジトリに奪われ、古いリンクが新しい方を指すようになる） | — |
| 2 | ~~GitHub Packages で、`io.github.duwenji.sanpoguide:prompts`（1.x を今のリポジトリから公開済み）の 2.0.0 を、新しいリポジトリから公開できるか~~ → できなかった（422）。古いパッケージを削除して解決（2026-10-13、手順 4） | 手順 2 で確かめる。できなければ、パッケージの設定でリポジトリの結びつきを変えるか、最後の公開だけ今のリポジトリから行う |
| 3 | `git filter-repo` がこの PC に入っていない | 手順 1 で `pip install git-filter-repo` で入れる |
| 4 | ~~PR #54 が開いたまま~~ → 解決。#54 はマージされ、#55 で yorisoi-tutorial にそろえた（2026-10-13） | — |

リスク:
- コアとアプリを同時に直すとき、公開の手順が 1 つ増える（R-7 で手元の試しは楽にし、版の上げ下げの手間は残る）
- キットを公開すると、キットの公開の IF を守る必要が出てくる（今は同じリポジトリの中なので自由に変えていた）。当面は SanpoGuide とサイクリングのアプリだけが使うので、メジャー版を上げて変えてよいことにする
- Android のリソース（`kit-geo` の drawable）を AAR で配ると、アプリ側のリソースとの名前の衝突が見えにくくなる。手順 2 の APK の一覧の比較で確かめる

## 関連ドキュメント・参照リンク

- [ADR-003](../adr/ADR-003-companion-app-platform.md): エコシステムの方針（E-19・E-28）
- [RFC-002](RFC-002-platform-stage1.md): 段階 1 の切り出し（公開 R-11・CI R-13）
- [RFC-003](RFC-003-talk-decision.md): 段階 2 の話す判断のエンジン

## 変更履歴

| 日付 | 版 | 変更内容 | 変更者 |
|---|---|---|---|
| 2026-10-13 | 0.1 | 草案 | Claude |
| 2026-10-13 | 1.0 | 承認。未決事項 No.1（`yorisoi-sanpoguide`）・No.4 を解決 | Claude（承認: 開発者） |
| 2026-10-14 | 1.1 | R-3 と影響の表に、ADR-003 1.4 で `applicationId`・パッケージ名を改めたことを注記（計画の中身は変えない） | Claude |
