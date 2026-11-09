# yorisoi-platform 文書案内

この `docs/` は、付き添いアプリのエコシステム **yorisoi** で共有する方針・要件・技術契約・規程を収録します。アプリ固有の文書（手引きを含む）は各アプリのリポジトリにあります。

## 読む目的から探す

| 読む目的 | 文書 |
|---|---|
| yorisoi とリポジトリの役割を知る | [エコシステム概要](overview/yorisoi-ecosystem.md) |
| 関係者の呼び方、役割、共通用語を確認する | [関係者と共通用語](overview/roles-and-vocabulary.md) |
| 関係者の課題と未承認の対応案を確認する | [関係者の課題と対応案](overview/stakeholder-needs.md) |
| だれに何を提供し、費用をだれが持つかの検討を読む | [提供価値と事業の検討](overview/value-proposition.md) |
| エコシステム・チャンネル・素材・生成 AI の決まりを読む | [エコシステム](overview/yorisoi-ecosystem.md#決まり)、[チャンネル](overview/channels.md)、[素材](overview/materials.md)、[生成 AI の役割](overview/ai-roles.md) |
| 複数のアプリにまたがる機能の要件を読む | [よく通る道](requirements/REQ-FREQUENT-WAYS.md)、[スタジオ](requirements/REQ-STUDIO.md)、[一言](requirements/REQ-NOTES.md)、[周りの見どころと雑談](requirements/REQ-NEARBY-TALK.md) |
| システム境界の形式・動作を確認する | [API 契約一覧](contracts/README.md) |
| チャンネル審査の基準を確認する | [審査ポリシー](policies/channel-review.md) |
| スタジオの決まりを確認する | [スタジオの決まりと素材提供者の承認の基準](policies/studio-rules.md) |
| 一言の決まりを確認する | [一言の利用規約](policies/notes-terms.md) |
| SanpoGuide の操作・試用・運用手順を読む | [SanpoGuide 手引き](https://github.com/duwenji/yorisoi-sanpoguide/blob/main/docs/manual/README.md) |
| エコシステムの未完了作業を確認する | [エコシステム TODO](TODO.md) |
| 秘密メッセージ OpenAPI を検証する | [OpenAPI 手順](contracts/api/README.md) |

`docs/` の下は、`overview/`（全体像・用語・方針）、`requirements/`（要件）、`contracts/`（契約。機械が読む定義は `contracts/api/`）、`policies/`（規程）と `TODO.md` に分けています。各アプリと yorisoi-console も同じ分け方（`design/`・`operations/`・`manual/` などを含む）にそろえています。

## 文書の正本

方針の文書（overview）はエコシステムの決まりを、API 契約は境界の形式と振る舞いを定めます。ポリシーは承認済み決定に沿う審査・運用基準、ガイドは読者別の操作手順、TODO は未完了作業の記録です。未承認の提案や TODO を承認済み仕様として扱いません。詳細は[エコシステム概要](overview/yorisoi-ecosystem.md)を参照してください。

## 文書の置き場所

| リポジトリ | 正本とする情報 |
|---|---|
| **yorisoi-platform**（このリポジトリ） | 複数リポジトリが従う方針、契約、共通語彙、審査基準 |
| 各アプリ（[yorisoi-sanpoguide](https://github.com/duwenji/yorisoi-sanpoguide) など） | アプリ固有の設計、画面、プロンプト、公開・プライバシー手続き |
| [yorisoi-console](https://github.com/duwenji/yorisoi-console) | 管理システムの実装・運用、管理 API、データモデル、配備 |
| [yorisoi-tutorial](https://github.com/duwenji/yorisoi-tutorial) | 学習用の説明。決まりは定めず、正本へ案内する |

複数のリポジトリが従う決まりはこのリポジトリに置きます。アプリや管理システム固有の説明は、それぞれのリポジトリを正本とし、ここからは参考情報としてリンクします。

## 文書の識別子

方針は `ECOSYSTEM`・`CHANNELS`・`MATERIALS`・`AI-ROLES`、契約は `API-CHANNEL-LIST` のように主題の名前で呼び、決まりは番号を添えて指します（例: `MATERIALS D-38`）。番号は詰めません。別リポジトリの番号付き文書を指すときは、リポジトリ名を付けます（例: `console ARCH-001`）。

方針を検討している間だけ `docs/decisions/` に ADR を置き、承認したら決まりを方針の文書や契約に移して ADR は消します（[エコシステム](overview/yorisoi-ecosystem.md#判断を検討するとき)）。
