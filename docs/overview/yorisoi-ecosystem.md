# yorisoi エコシステム

| 項目 | 内容 |
|---|---|
| 対象 | yorisoi のエコシステムと共通基盤 |
| 読む人 | アプリ開発者、チャンネル提供者、素材提供者、運営、学習者 |
| 状態 | 概要 |
| 関連 | [関係者と用語](roles-and-vocabulary.md)、[意思決定記録](../decisions/README.md)、[技術契約](../contracts/README.md) |

**yorisoi** は、場所や状況に応じてアプリ利用者に語りかける付き添いアプリと、それらが共通に使う仕組みからなるエコシステムです。アプリは、散策を支援する **SanpoGuide** と、自転車の走行に付き添う **CycleGuide** です。

## 構成

| 名前 | 役割 | 文書の置き場所 |
|---|---|---|
| **yorisoi-platform** | 付き添いアプリ間で共有するライブラリ、形式、語彙を提供する | このリポジトリの [共通文書](../README.md) と実装 |
| **アプリ** | 共通基盤を使い、対象の過ごし方に合った体験を提供する | 各アプリのリポジトリ（[yorisoi-sanpoguide](https://github.com/duwenji/yorisoi-sanpoguide)・[yorisoi-cycleguide](https://github.com/duwenji/yorisoi-cycleguide)）。SanpoGuide は[アプリ別手引き](../apps/sanpoguide/manual/README.md)も参照 |
| **yorisoi-console** | チャンネルや素材集の申請・審査・配信を扱う管理システム。運営の AI の中継、スポットのタイルの配信、スタジオ（場所に残る一言と提供者の掲載）も置く | [yorisoi-console の文書](https://github.com/duwenji/yorisoi-console/tree/main/docs) |
| **yorisoi-tutorial** | 仕組みを学ぶための説明を提供する。規範や契約の正本にはしない | [yorisoi-tutorial](https://github.com/duwenji/yorisoi-tutorial) |

## 文書の正本と置き場所

このリポジトリは、複数のリポジトリが従う決まりを扱います。画面や配備など特定アプリ・システムだけの実装手順は、そのリポジトリの文書を正本にします。

| 文書の種類 | 用途 | 正本 |
|---|---|---|
| 意思決定記録（ADR） | エコシステムの方針と設計判断 | [決定記録一覧](../decisions/README.md) |
| 技術契約（API） | 形式、境界、相互運用上の振る舞い | [技術契約一覧](../contracts/README.md) |
| ポリシー | 合意した方針に基づく審査・運用基準 | [チャンネル審査基準](../policies/channel-review.md)、[一言の利用規約](../policies/studio-note-terms.md)、[掲載の決まり](../policies/studio-placement.md) |
| 用語・関係者 | エコシステムの共通概念と呼び方 | [関係者と用語](roles-and-vocabulary.md) |
| アプリ別手順 | 個別アプリの操作・試用・運用 | [SanpoGuide 手引き](../apps/sanpoguide/manual/README.md) |
| 未完了の作業 | エコシステムに関する未完了事項と検討項目 | [エコシステム TODO](../work/ecosystem-todo.md) |

### 文書の権威

承認済み ADR は方針・決定、API 契約はシステム境界の形式・振る舞いを定めます。ポリシーは承認済みの決定に沿った運用基準、ガイドは読者が手順を実行するための説明です。TODO や未承認の提案は、決定済みの仕様を上書きしません。文書間に矛盾があれば、矛盾を解消するまでは関連する決定や契約を確認し、未決であることを明示します。

## 変更の境界

- このリポジトリの契約や語彙を変えるときは、利用するアプリと管理システムへの影響を確認します。
- 特定アプリの挙動・画面・公開状態は、そのアプリ固有の情報として扱います。現在の SanpoGuide の手順や公開状態を、他のアプリにも当てはまる仕様として読まないでください。
- 学習用の説明は、対応する ADR・契約・ポリシーを正本として参照します。

SanpoGuide 固有の現在の状態は、[SanpoGuide 手引きの案内](../apps/sanpoguide/manual/README.md)で確認できます。
