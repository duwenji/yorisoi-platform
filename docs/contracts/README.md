# API 契約一覧

この一覧は、yorisoi エコシステムでアプリ・管理システム・外部サービスの境界に適用する契約の正本です。承認済みの契約本文を優先し、未完了の実装作業は [エコシステム TODO](../work/ecosystem-todo.md) で管理します。

## 契約

| Canonical ID | 契約 | 主な境界 | 旧 ID |
|---|---|---|---|
| `API-SECRET-MESSAGES` | [秘密メッセージ サーバー API](API-SECRET-MESSAGES.md) | アプリと、位置に紐づく秘密メッセージを扱う準拠サーバー | `API-001` |
| `API-CHANNEL-LIST` | [承認済みチャンネル・リスト](API-CHANNEL-LIST.md) | チャンネル管理システムとアプリ | `API-002` |
| `API-CHANNEL-PACKAGE` | [チャンネル・パッケージ形式](API-CHANNEL-PACKAGE.md) | チャンネル提供者・アプリ・審査ツール | `API-003` |
| `API-MATERIAL-PRODUCER` | [素材提供者インタフェース](API-MATERIAL-PRODUCER.md) | 素材提供者と、素材を利用するチャンネル・アプリ | `API-004` |
| `API-AI-RELAY` | [運営の AI 中継](API-AI-RELAY.md) | アプリと運営の AI の中継 | — |
| `API-SPOT-TILES` | [スポットのタイル](API-SPOT-TILES.md) | アプリと運営のスポットのタイルの配信 | — |
| `API-STUDIO` | [スタジオ](API-STUDIO.md)（草案） | アプリと、一言と掲載の置き場と配信のサーバー | — |

各契約はエコシステム共通の境界を定めます。画面や運用など特定アプリに限る説明は、契約本文でその範囲を示します。

## 機械可読な定義

スポットのタイルの形は JSON Schema [`api/spot-tile.schema.json`](../api/spot-tile.schema.json) です。秘密メッセージ API の機械可読な定義は [`api/sanpo-messages.openapi.yaml`](../api/sanpo-messages.openapi.yaml) にあり、検証方法は [`api/README.md`](../api/README.md) にあります。OpenAPI は契約の機械可読な一部であり、判断の背景やスキーマで表現できない規則は契約本文を参照してください。
