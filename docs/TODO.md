# エコシステム TODO

エコシステム全体で残っている作業と、決めることを記録します。アプリだけの作業は各アプリの TODO（[SanpoGuide](https://github.com/duwenji/yorisoi-sanpoguide/blob/main/docs/TODO.md)・[CycleGuide](https://github.com/duwenji/yorisoi-cycleguide/blob/main/docs/TODO.md)）、管理システムの作業は [yorisoi-console の TODO](https://github.com/duwenji/yorisoi-console/blob/main/docs/TODO.md) を参照してください。

## 共通基盤

- [ ] CycleGuide（ECOSYSTEM E-23。要件は [yorisoi-cycleguide の要件定義](https://github.com/duwenji/yorisoi-cycleguide/blob/main/docs/requirements/cycleguide.md)）。段階 1・2 は内部テストに出ている。残りは各アプリの TODO
- [ ] 新しいアプリの雛形と、要望からプロファイル・ルール・プロンプト・組み込みのチャンネルを生成するスキル（CycleGuide の後。ECOSYSTEM E-20）

## 運営の AI（中継）

[API-AI-RELAY](contracts/API-AI-RELAY.md)。中継は開発用（yorisoi-console の `YorisoiAiRelay-dev`）だけを配備し、両アプリのリリース版も開発用を使っています。

- [ ] 中継のモデルを Claude Haiku 4.5 に切り替えることがあれば、その前にアカウントで Anthropic のユースケースを申告する
- [ ] 運営の AI 全体の 1 か月の予算を決める（本番に出す前。API-AI-RELAY 未決事項 No.2）
- [ ] 本番の中継の配備と Play Integrity の確認。アプリ側の作業は各アプリの TODO「Google Play での公開」

## 準備役（その場で調べて話す）

[AGENT](overview/agent.md)・[ウェブの除外リストの決まり](policies/web-exclusion.md)。台帳・除外リスト・季節の観測の配信と中継の用途 `agent`・`hints` は開発用で動いていて、両アプリと審査の見本（validator）に入っています。エミュレータでは、両アプリとも JVM の部品（2.55.0）で散歩・走行の始めに福井市の筋（暦 → 季節）ができ、CycleGuide では雑談の「調べた話」の番に筋の 1 段を出どころを添えて話すのを確かめた。

- [ ] 実機での確認（AC-15）
- [ ] チャンネルの説明に「その場で調べて話します」「自治体などのウェブのページも読みます」を出す（A-10。両アプリ）
- [ ] 「このアプリについて」に気象庁の出典と、編集の責任が運営にあることを書く（A-28。両アプリ）
- [ ] 準備を「Wi-Fi のときだけ」にする設定と、充電中で Wi-Fi の朝によく通る道の市区町村の筋を先に作ること（A-35。両アプリ）
- [ ] 利用者の知らせに、その発言に使った手がかりの出どころを添える（A-50。両アプリ）
- [ ] 審査の警告（使えない場面を書いたもの・準備しないチャンネルの `plan`）と、`sources` を除外リストに照らす確かめ（A-9。yorisoi-console の機械の確認）
- [ ] 運営の画面のエディタに `agent` の欄を足し、`vocabulary.json` に場面と出どころの種類を写す（A-9・A-52。yorisoi-console）
- [ ] 同じ市区町村の筋どうしで、最初の段の事実が重ならないようにする（審査の見本で、筋 2 本がどちらも同じ暦の事実から始まった）
- [ ] プロンプトのファイルの `{{! }}` の中の `ADR-AGENT` の参照を、次にプロンプトの版を上げるときに `AGENT` に直す（両アプリ。ファイルはハッシュで固めているため）
- [ ] 技術チュートリアルの追従

## スポットのタイル

[API-SPOT-TILES](contracts/API-SPOT-TILES.md)。配信は開発用（yorisoi-console の `YorisoiSpotTiles-dev`）で、両アプリのリリース版も開発用を使っています。

- [ ] 技術チュートリアルの追従
- [ ] 本番の配信を作る時期を決める（API-SPOT-TILES 未決事項 No.2）
- [ ] スポットの Wikidata の情報（[REQ-WIKIDATA](requirements/REQ-WIKIDATA.md)）: バッチ（X-1）、kit-geo（X-2）、両アプリ（X-3・X-4）、技術チュートリアル（X-5）、実機の確認（X-6）
- [ ] 地域の催し（[REQ-LOCAL-EVENTS](requirements/REQ-LOCAL-EVENTS.md)）: 実機の確認（AC-11）。エミュレータでは、催しの日付を今日に移した仮の配信で、両アプリの雑談・SanpoGuide の予告・記録を確かめた。両アプリ 1.14.0 を内部テストに出している（話し方の決まりの最後の直しはプロンプト SanpoGuide 4.9.2・CycleGuide 0.7.2 で、次の版から）。バッチは開発用で毎週動いている

## その場の素材（スタジオ・一言）

[REQ-STUDIO](requirements/REQ-STUDIO.md)・[REQ-NOTES](requirements/REQ-NOTES.md)・[MATERIALS](overview/materials.md)・[API-LOCAL-MATERIALS](contracts/API-LOCAL-MATERIALS.md)。サーバーは開発用（yorisoi-console の `YorisoiLocalMaterials-dev`）だけです。アプリ側の部品は [kit-local-materials](../kit-local-materials/README.md)。

- [ ] 本番の配備（中継の本番の後）。Play のデータ セーフティとコンテンツのレーティングを、各アプリの `docs/operations/play-console.md` のとおりに入れ直す（REQ-STUDIO・REQ-NOTES N-3）
- [ ] 技術チュートリアルの追従

決めること（REQ-STUDIO の未決事項）:
- U-4 端末での文字起こしの精度（屋外・風・自転車）、U-5 聞く設定の既定、U-6 両アプリで一言・スタジオを分けるか、U-7 電池の目標、U-8 CycleGuide のメディアボタンでのいいね
- U-9 スタジオの有料化、U-10 画像・声・映像・即時の配信、U-12 費用の見積もり

## 第三者のチャンネル

- [ ] 関係者ごとの対応案（[関係者の課題と対応案](overview/stakeholder-needs.md)）を承認し、公開の前に要るもの（W-1 内蔵のリスト）を進める
- [ ] 審査にかかる期間の目安と、運営が複数になる場合（承認を 2 人にするか、権限を分けるか）

決めること:
- CHANNELS の未決事項（第三者のチャンネルのカスタマイズ）

## よく通る道

要件は [REQ-FREQUENT-WAYS](requirements/REQ-FREQUENT-WAYS.md)。両アプリに入っている。

- [ ] CycleGuide のプロンプト・パック 0.1.2 の公開（`prompts-v0.1.2`。前に話した見どころの見本 `ride-guide-again` が増える）と、yorisoi-console の validator の追従（今は 0.1.0）
- [ ] 散策の見つけ方の値（300 m）を散歩の記録で確かめる（U-1）

## 位置に紐づく秘密メッセージ

契約は [API-SECRET-MESSAGES](contracts/API-SECRET-MESSAGES.md)（OpenAPI は [api/](contracts/api/README.md)）。接続先は URL で設定し、API に準拠していればサーバーの実装方法・置き場所は問わない。アプリでの実装は各アプリの TODO で進める。

- [ ] 準拠テスト: 任意のサーバーの URL に対して流せるテスト（API-SECRET-MESSAGES の確認観点 V-01〜V-18）。Kotlin（JVM）＋ Tink を想定
- [ ] 参照実装のサーバー（開発・テスト用。技術は自由）

決めること:
- 位置の偽装（位置情報の偽装アプリ）で開けられることへの対策をどこまでやるか
- 公開メッセージの不適切な投稿への対策（通報・削除）。一言の仕組み（通報・自動で隠す・締め出し・置けない区画）を使えるか、MATERIALS U-2 とあわせて決める
- 私有地・危険な場所に置かせない配慮

## 3 秒ごとの Tick

判定を 3 秒ごとにし、オンエアが空いたらチャンネルの `tick` のきっかけ（`idle` を一本化）の素材、なければ雑談・季節の歌を出す（REQ-NEARBY-TALK D-19・D-20・F-14・F-35、REQ-KOYOMI F-42、API-CHANNEL-PACKAGE「語りの合間のきっかけ」）。両アプリ。

- X-1: core-format の `tick`・`idle` の読み替え・`gapMin`・`tick` で使える素材の検査、kit-channel のきっかけの照合（platform）
- X-2: validator とチャンネルの編集画面（yorisoi-console）
- X-3: 3 秒の Tick、オンエア、空いたときの順番、雑談と歌の条件、記録（yorisoi-sanpoguide）
- X-4: X-3 と同じ。信号の近く・速いときに黙る条件は残す（yorisoi-cycleguide）
- X-5: 各アプリの設計・手引き、console の手順書、yorisoi-tutorial の追従

## 話の空白

話の間隔を話し終わりからの「黙る時間」で決め（普通 45 秒）、雑談を 3 つまとめて作って取り置き、解説の続きも話す（[REQ-NEARBY-TALK](requirements/REQ-NEARBY-TALK.md) D-7・D-21〜D-24・F-36〜F-41）。両アプリ。

- 実機で、黙る時間（にぎやか 20 秒・静か 4 分・ロングライド 90 秒）と続き・取り置きの聞こえ方を確かめる（記録の `said.silenceMs`・`kept`）
- 両アプリのプロンプトの `companion/events/chat.md` の頭のコメントに残る `ADR-SILENCE` の参照を、次にプロンプトの版を上げるとき REQ-NEARBY-TALK に直す

## 共通の検討事項

- **収益化**: 広告、有料プラン、提携。既存の広告 SDK は、位置情報を外部に送らない方針（API-CHANNEL-PACKAGE F-10）と合わないので使わない方向。だれに何を提供し、だれが費用を持つかは[提供価値と事業の検討](overview/value-proposition.md)で検討中
- **公開サーバーの負荷**: スポットは運営のタイルを先に使い、Overpass は予備（[スポットのタイル](#スポットのタイル)）。Open-Meteo の公開サーバーは、アプリ利用者が増える前に利用ポリシーを確認し、自前のサーバーや商用のサービスを検討する
