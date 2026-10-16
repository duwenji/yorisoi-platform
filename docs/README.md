# 文書

付き添いアプリのエコシステム **yorisoi** 全体に関わる文書を、このリポジトリに置いています。残りの作業は [TODO.md](TODO.md) にあります。

## 一覧

| 文書 | 内容 |
|---|---|
| [ADR-003](adr/ADR-003-companion-app-platform.md) | エコシステムの方針 |
| [ADR-001](adr/ADR-001-third-party-channel-curation.md) | 第三者のチャンネルの審査と署名 |
| [ADR-002](adr/ADR-002-material-producers.md) | 素材提供者 |
| [API-001](secret-messages-api.md) | 秘密メッセージ サーバー API。OpenAPI は [api/](api/README.md) |
| [API-002](channel-list-api.md) | 承認済みチャンネル・リスト |
| [API-003](channel-package-format.md) | チャンネル・パッケージの形式 |
| [API-004](material-producer-api.md) | 素材提供者 IF と素材集の形式 |
| [channel-review-policy.md](channel-review-policy.md) | 審査基準 |
| [stakeholders.md](stakeholders.md) | 関係者の呼び方と、チャンネルという考え方 |

## 置き場所

| リポジトリ | 置く文書 |
|---|---|
| **yorisoi-platform**（ここ） | 複数のリポジトリが従う決まり。エコシステムの ADR、アプリとサーバー・管理システムの間の契約、関係者と用語、審査基準、エコシステム全体の TODO |
| 各アプリ（[yorisoi-sanpoguide](https://github.com/duwenji/yorisoi-sanpoguide) など） | そのアプリだけの設計と手続き。構造図、チャンネルのアプリ側、プロンプト、ストアへの公開、プライバシーポリシー |
| [yorisoi-console](https://github.com/duwenji/yorisoi-console) | 管理システムの実装と運用。構成、管理 API、データモデル、設計（DES）、利用ガイド、配備 |
| [yorisoi-tutorial](https://github.com/duwenji/yorisoi-tutorial) | 学ぶための説明。決まりは書かず、正本へリンクする |

迷ったときは「2 つ以上のリポジトリがこの文書に従うか」で決めます。従うならここに置きます。ここの文書からアプリや管理システムの文書へは、例や関連として指すだけにします。

## 文書の番号

番号はリポジトリごとに振ります。別のリポジトリの文書を番号で呼ぶときは、リポジトリ名を添えます（先頭の `yorisoi-` は省いてよい）。

| 書き方 | 指す文書 |
|---|---|
| platform ADR-001 | このリポジトリの [ADR-001](adr/ADR-001-third-party-channel-curation.md) |
| platform API-001 | このリポジトリの [API-001](secret-messages-api.md) |
| console ADR-001 | yorisoi-console の ADR-001（AWS の構成） |
| console API-001 | yorisoi-console の API-001（管理 API） |

同じリポジトリの中では、番号だけで呼びます。
