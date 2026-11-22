# ADR-AGENT の下書き

[ADR-AGENT](../ADR-AGENT.md) を承認するまで、決まりを移す先の文書の案をここに置きます。承認したら、表の「移す先」に移し、このフォルダは消します。

| 下書き | 中身 | 移す先 |
|---|---|---|
| [data/agent-tools.v1.json](data/agent-tools.v1.json) | 準備役が頼む道具の定義（A-11・A-12・A-15・A-45） | `docs/contracts/api/agent-tools.v1.json` |
| [data/web-sources.schema.json](data/web-sources.schema.json) | 出どころの台帳の配信ファイルの形（A-21・A-22・A-26） | `docs/contracts/api/web-sources.schema.json` |
| [data/web-exclusion.schema.json](data/web-exclusion.schema.json) | 除外リストの配信ファイルの形（A-24〜A-26） | `docs/contracts/api/web-exclusion.schema.json` |
| [data/season-obs.schema.json](data/season-obs.schema.json) | 季節の観測の配信ファイルの形（A-27・A-28） | `docs/contracts/api/season-obs.schema.json` |
| [data/text-safety-vectors.json](data/text-safety-vectors.json) | 機械の確認の見本（A-18・A-52 の core-text） | `docs/contracts/api/text-safety-vectors.json` |
| [data/web-url-vectors.json](data/web-url-vectors.json) | 読んでよい URL・同じサイト・除外の照合の見本（A-17・A-24） | `docs/contracts/api/web-url-vectors.json` |
| [policies/web-exclusion.md](policies/web-exclusion.md) | 除外リストに足す・外す基準と申し立て（A-24・A-25） | `docs/policies/web-exclusion.md` |
| [revisions/](revisions/) | 関連文書の改訂案（決まりごとの、今の文と改めた文） | 各文書 |

関連文書の改訂案:

| 案 | 対象 |
|---|---|
| [revisions/core-format.md](revisions/core-format.md) | core-format の語彙と確かめ |
| [revisions/API-CHANNEL-PACKAGE.md](revisions/API-CHANNEL-PACKAGE.md) | チャンネルの形式 `agent` |
| [revisions/AI-ROLES.md](revisions/AI-ROLES.md) | 役割と根拠の安全 |
| [revisions/API-AI-RELAY.md](revisions/API-AI-RELAY.md) | 中継の用途・モデル・長さ |
| [revisions/REQ-SPOT-REFERENCES.md](revisions/REQ-SPOT-REFERENCES.md) | 参考の文章と調べた手がかりの関係 |
| [revisions/REQ-KOYOMI.md](revisions/REQ-KOYOMI.md) | 市区町村の決め方 |
| [revisions/REQ-NEARBY-TALK.md](revisions/REQ-NEARBY-TALK.md) | 雑談の話題「調べた話」 |
| [revisions/channel-review.md](revisions/channel-review.md) | 審査の項目 |
