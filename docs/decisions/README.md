# 意思決定記録

承認済み ADR は yorisoi の方針と設計判断の正本です。実装や契約の詳細は[技術契約一覧](../contracts/README.md)、個別アプリの操作は各アプリの手引きを参照してください。

エコシステムは**チャンネル**と**素材**の 2 つで組み立てます（ADR-ECOSYSTEM-PLATFORM E-28）。チャンネルは ADR-CHANNELS、素材は ADR-MATERIALS が扱います。

## 一覧

| Canonical ID | 旧 ID | 文書 | 主題 |
|---|---|---|---|
| `ADR-ECOSYSTEM-PLATFORM` | ADR-003 | [エコシステム共通基盤](ADR-ECOSYSTEM-PLATFORM.md) | チャンネルと素材、層、アプリプロファイル、共通語彙、アプリをまたぐチャンネルと素材集 |
| `ADR-CHANNELS` | ADR-001・ADR-CHANNEL-CURATION | [チャンネル](ADR-CHANNELS.md) | チャンネルの役割、第三者のチャンネルの承認・署名・取り下げ |
| `ADR-MATERIALS` | ADR-002・ADR-MATERIAL-PRODUCERS・ADR-STUDIO・ADR-STUDIO-MATERIAL | [素材](ADR-MATERIALS.md) | 素材の種類と作る人・責任、出し方を決める順番、人の発言の層、素材集、スタジオ、一言 |
| `ADR-AI-ROLES` | ADR-004 | [生成 AI の役割と責任](ADR-AI-ROLES.md) | 根拠・演出・実演・枠・考査、費用、審査 |
| `ADR-AI-RELAY` | — | [運営による AI の中継](ADR-AI-RELAY.md) | 運営の AI と自分のキー、無料の上限、Bedrock |
| `ADR-SPOT-TILES` | — | [スポットのタイル](ADR-SPOT-TILES.md) | スポットの取り先、タイルの作り方と配り方、Overpass は予備 |

## 旧 ID の決定の番号

ADR-CHANNEL-CURATION の C-1〜C-14 と、ADR-MATERIAL-PRODUCERS の D-1〜D-27 は、番号を変えずに ADR-CHANNELS・ADR-MATERIALS にあります。ADR-STUDIO と ADR-STUDIO-MATERIAL の決定は、次の番号で ADR-MATERIALS にあります。ADR-MATERIALS では、スタジオと一言を組にせず、種類ごとの節と、素材の性質ごとの決まりに分けて書いています。

| 旧 ID | ADR-MATERIALS | 主題 |
|---|---|---|
| ADR-STUDIO S-1 | D-38 | 人の言葉をそのまま出す素材（人の発言の層） |
| ADR-STUDIO S-2 | D-39 | スタジオを出す人と責任 |
| ADR-STUDIO S-3 | D-41 | 一言を置いた人の責任と運営の役割 |
| ADR-STUDIO S-4 | D-42 | 一言を置いた人の識別 |
| ADR-STUDIO S-5 | D-43 | 配る一覧とブロック |
| ADR-STUDIO S-6 | D-40 | 宣伝との関係 |
| ADR-STUDIO S-7 | D-51 | 区画ごとに運営から取る素材の配信の契約と置き場所 |
| ADR-STUDIO-MATERIAL M-1・M-8 | D-44 | 区画ごとに運営から取る素材（組み込みの素材提供者と属性） |
| ADR-STUDIO-MATERIAL M-2 | D-45 | きっかけ |
| ADR-STUDIO-MATERIAL M-3 | D-46・D-55 | チャンネルが決められること、スタジオの分類 |
| ADR-STUDIO-MATERIAL M-4 | D-47 | チャンネルが変えられないこと |
| ADR-STUDIO-MATERIAL M-5 | D-48・D-53・D-54 | 書いていない種類、スタジオの省略形、一言の省略形 |
| ADR-STUDIO-MATERIAL M-6 | D-49 | 前置き |
| ADR-STUDIO-MATERIAL M-7 | D-50 | 条件で選ぶ素材の責任と審査 |
| ADR-STUDIO-MATERIAL M-9 | D-40 | 宣伝との関係 |
| ADR-STUDIO-MATERIAL M-10 | D-2・D-52 | 素材の種類、用語 |

## 文書の呼び方

このリポジトリでは canonical ID を使います。履歴・既存の外部資料に旧番号が出てくる場合は、上の表で読み替えてください。別のリポジトリの番号付き文書を指す場合は、リポジトリ名を併記します。
