# エコシステム TODO

エコシステム全体で残っている作業と、決めることを記録します。アプリだけの作業は各アプリの TODO（[SanpoGuide](https://github.com/duwenji/yorisoi-sanpoguide/blob/main/docs/TODO.md)）、管理システムの作業は [yorisoi-console の TODO](https://github.com/duwenji/yorisoi-console/blob/main/docs/TODO.md) を参照してください。

## ADR の追従タスク

- [ ] CycleGuide（ADR-ECOSYSTEM-PLATFORM T-2。要件は [yorisoi-cycleguide の要件定義](https://github.com/duwenji/yorisoi-cycleguide/blob/main/docs/requirements.md)）
- [ ] 新しいアプリの雛形と、要望からプロファイル・ルール・プロンプト・組み込みのチャンネルを生成するスキル（ADR-ECOSYSTEM-PLATFORM T-1。T-2 の後）

## 運営の AI（中継）

[ADR-AI-RELAY](../decisions/ADR-AI-RELAY.md)・[API-AI-RELAY](../contracts/API-AI-RELAY.md)。

- [ ] 中継のモデルを Claude Haiku 4.5 に切り替えることがあれば、その前にアカウントで Anthropic のユースケースを申告する（T-6）
- [ ] 中継の実装と配備（yorisoi-console の `infra/` に別スタック。T-3）
- [ ] `kit-llm` とアプリの実装（T-4）と、アプリの文書・手引き・プライバシーポリシー・Play の申告の改訂（T-5）
- [ ] 運営の AI 全体の 1 か月の予算を決める（T-7。本番に出す前）

## 第三者のチャンネル

- [ ] 関係者ごとの対応案（[関係者の課題と対応案](../overview/stakeholder-needs.md)）を承認し、公開の前に要るもの（W-1 内蔵のリスト）を進める
- [x] [審査基準](../policies/channel-review.md)を承認する
- [ ] 審査にかかる期間の目安と、運営が複数になる場合（承認を 2 人にするか、権限を分けるか）

決めること:
- ADR-CHANNEL-CURATION の未決事項（第三者のチャンネルのカスタマイズ）

## よく通る道

要件は [REQ-FREQUENT-WAYS](../requirements/REQ-FREQUENT-WAYS.md)（承認済み）。

- [x] 設計: kit-geo の `FrequentWay`（形・保存・見つけ方・道に沿った前もっての取得）。D-8〜D-12
- [x] kit-geo の実装と公開（v2.20.0。画面を共通にした v2.21.0）
- [x] CycleGuide: 画面・話・振り返り。要件の F-16 を書き直す（X-3）。1.6.0 (9) を内部テストに出した
- [x] SanpoGuide: 画面・話・振り返り（再訪の判定はそのまま使い、24 時間以内は話さない判定を足した。D-11）。1.6.0 (11) を内部テストに出した
- [x] 両アプリのプライバシーポリシーの「端末内の記録」を書き足す（N-2）
- [ ] CycleGuide のプロンプト・パック 0.1.2 の公開（`prompts-v0.1.2`。前に話した見どころの見本 `ride-guide-again` が増える）と、yorisoi-console の validator の追従（今は 0.1.0）
- [ ] 実機での確認: 3 回通った道がよく通る道になる、Overpass が混んでいる日にも前方の見どころが空白にならない、前の日に話した見どころを別の話で話す
- [ ] 散策の見つけ方の値（300 m）を散歩の記録で確かめる（U-1）

## 位置に紐づく秘密メッセージ

契約は [API-SECRET-MESSAGES](../contracts/API-SECRET-MESSAGES.md)（OpenAPI は [api/](../api/README.md)）。接続先は URL で設定し、API に準拠していればサーバーの実装方法・置き場所は問わない。アプリでの実装は各アプリの TODO で進める。

- [ ] 準拠テスト: 任意のサーバーの URL に対して流せるテスト（API-SECRET-MESSAGES の確認観点 V-01〜V-18）。Kotlin（JVM）＋ Tink を想定
- [ ] 参照実装のサーバー（開発・テスト用。技術は自由）

OpenAPI の検証: `docs/api` で `npx @redocly/cli lint sanpo-messages.openapi.yaml`

決めること:
- 位置の偽装（位置情報の偽装アプリ）で開けられることへの対策をどこまでやるか
- 公開メッセージの不適切な投稿への対策（通報・削除）
- 私有地・危険な場所に置かせない配慮

## 共通の検討事項

- **サーバーの要否（決定済み）**: 他人宛ての秘密メッセージにはサーバーが必要で、アプリは接続先 URL を設定できる。詳細は [API-SECRET-MESSAGES](../contracts/API-SECRET-MESSAGES.md) を参照。SanpoGuide の現在の実装状況は [SanpoGuide TODO](https://github.com/duwenji/yorisoi-sanpoguide/blob/main/docs/TODO.md) を参照
- **収益化**: 広告、有料プラン、提携。既存の広告 SDK は、位置情報を外部に送らない方針（API-CHANNEL-PACKAGE F-10）と合わないので使わない方向
- **公開サーバーの負荷（SanpoGuide の現行実装）**: `kit-geo` は Overpass・Open-Meteo の公開サーバーを使っている。アプリ利用者が増える前に利用ポリシーを確認し、自前のサーバーや商用のサービスを検討する
