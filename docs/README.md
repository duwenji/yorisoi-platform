# yorisoi-platform 文書案内

この `docs/` は、付き添いアプリのエコシステム **yorisoi** で共有する方針・技術契約と、**SanpoGuide** の手引きを収録します。両者の対象範囲を混同しないよう、共通文書とアプリ固有文書を分けています。

## 読む目的から探す

| 読む目的 | 文書 |
|---|---|
| yorisoi とリポジトリの役割を知る | [エコシステム概要](overview/yorisoi-ecosystem.md) |
| 関係者の呼び方、役割、共通用語を確認する | [関係者と共通用語](overview/roles-and-vocabulary.md) |
| 関係者の課題と未承認の対応案を確認する | [関係者の課題と対応案](overview/stakeholder-needs.md) |
| だれに何を提供し、費用をだれが持つかの検討を読む | [提供価値と事業の検討](overview/value-proposition.md) |
| 設計方針と決定の根拠を読む | [ADR 一覧](decisions/README.md) |
| 複数のアプリにまたがる機能の要件を読む | [よく通る道](requirements/REQ-FREQUENT-WAYS.md)、[スタジオ](requirements/REQ-STUDIO.md) |
| システム境界の形式・動作を確認する | [API 契約一覧](contracts/README.md) |
| チャンネル審査の基準を確認する | [審査ポリシー](policies/channel-review.md) |
| スタジオの一言と掲載の決まりを確認する | [一言の利用規約](policies/studio-note-terms.md)、[掲載の決まりと提供者の承認の基準](policies/studio-placement.md) |
| SanpoGuide の操作・試用・運用手順を読む | [SanpoGuide 手引き](apps/sanpoguide/manual/README.md) |
| エコシステムの未完了作業を確認する | [エコシステム TODO](work/ecosystem-todo.md) |
| 秘密メッセージ OpenAPI を検証する | [OpenAPI 手順](api/README.md) |

## 文書の正本

承認済み ADR はエコシステムの方針・決定を、API 契約は境界の形式と振る舞いを定めます。ポリシーは承認済み決定に沿う審査・運用基準、ガイドは読者別の操作手順、TODO は未完了作業の記録です。未承認の提案や TODO を承認済み仕様として扱いません。詳細は[エコシステム概要](overview/yorisoi-ecosystem.md)を参照してください。

## 文書の置き場所

| リポジトリ | 正本とする情報 |
|---|---|
| **yorisoi-platform**（このリポジトリ） | 複数リポジトリが従う方針、契約、共通語彙、審査基準 |
| 各アプリ（[yorisoi-sanpoguide](https://github.com/duwenji/yorisoi-sanpoguide) など） | アプリ固有の設計、画面、プロンプト、公開・プライバシー手続き |
| [yorisoi-console](https://github.com/duwenji/yorisoi-console) | 管理システムの実装・運用、管理 API、データモデル、配備 |
| [yorisoi-tutorial](https://github.com/duwenji/yorisoi-tutorial) | 学習用の説明。決まりは定めず、正本へ案内する |

複数のリポジトリが従う決まりはこのリポジトリに置きます。アプリや管理システム固有の説明は、それぞれのリポジトリを正本とし、ここからは参考情報としてリンクします。

## ADR・API の識別子

このリポジトリ内では、ADR/API は主題ベースの canonical ID と正本のパスで参照します。旧番号は各一覧で canonical ID に対応づけています。別リポジトリの番号付き文書を参照するときは、リポジトリ名も付けます（例: `console ADR-001`）。
