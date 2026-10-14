# yorisoi-platform

付き添いアプリのエコシステム **yorisoi**（寄り添い）の共通パッケージです。場所と状況に応じて AI が語りかけるアプリ（付き添いアプリ）が共通に使う部品を置いています。最初のアプリは [SanpoGuide](https://github.com/duwenji/yorisoi-sanpoguide)、管理システムは [yorisoi-console](https://github.com/duwenji/yorisoi-console) です。

方針は [ADR-003](docs/adr/ADR-003-companion-app-platform.md) にあります。

## モジュール

| モジュール | 種類 | 中身 |
|---|---|---|
| `core-format` | JVM | チャンネル・素材集の形式（API-003・API-004）、その確認と署名、承認済みチャンネル・リスト（API-002）、エコシステム共通の語彙。使う側で org.json が要る（Android には入っている） |
| `core-prompt` | JVM | プロンプトのテンプレート（Mustache の一部） |
| `kit-companion` | JVM | いま何を話すかの判定のエンジン（ルールの並びとゲート） |
| `kit-voice` | Android | 読み上げ（オーディオフォーカス）と背景音の合成 |
| `kit-llm` | Android | AI の接続（Claude・OpenAI 互換）と鍵の暗号化 |
| `kit-geo` | Android | スポット・ルート・写真・天気・地図 |
| `kit-mood` | Android | 雰囲気（時間帯・季節・空模様・場所）と、配色・絵・背景音 |

コアは Android に依存しません。管理システムがアプリと同じコードで確かめるためです。

## 使い方

すべてのモジュールを、同じ版で GitHub Packages に公開しています（座標は `io.github.duwenji.yorisoi:<モジュール名>:<版>`）。GitHub Packages の Maven は、公開のパッケージでも読むのにトークン（`read:packages`）が要ります。

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/duwenji/yorisoi-platform")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

GitHub Actions からは `GITHUB_TOKEN` で読めます（このリポジトリは公開なので、パッケージの設定は要りません）。

### ソースに差し替えて試す

アプリとこのリポジトリを同時に直すときは、アプリ側で composite build を使います。モジュールの名前と `group` が座標と同じなので、Gradle が公開の版をこのリポジトリのソースに自動で置き換えます。

```kotlin
// アプリの settings.gradle.kts
providers.gradleProperty("yorisoi.platformDir").orNull?.let { includeBuild(it) }
```

```
./gradlew assembleDebug -Pyorisoi.platformDir=../yorisoi-platform
```

## 開発

```
./gradlew test lint            # テストと lint（CI と同じ）
./gradlew publishToMavenLocal  # 手元の Maven に出す
```

## 公開

1. `gradle.properties` の `yorisoiVersion` を上げる（すべてのモジュールで 1 つの版）
2. `main` にマージしてから、タグ `v<版>` を push する。`.github/workflows/publish.yml` がテストして公開する

形式や語彙を変えたときは、管理システムとアプリへの影響を契約書（`docs/` の API-002〜004）で確かめてから上げてください。

## 文書

| 文書 | 内容 |
|---|---|
| [ADR-003](docs/adr/ADR-003-companion-app-platform.md) | エコシステムの方針 |
| [ADR-001](docs/adr/ADR-001-third-party-channel-curation.md) | 第三者のチャンネルの審査と署名 |
| [ADR-002](docs/adr/ADR-002-material-producers.md) | 素材提供者 |
| [API-002](docs/channel-list-api.md) | 承認済みチャンネル・リスト |
| [API-003](docs/channel-package-format.md) | チャンネル・パッケージの形式 |
| [API-004](docs/material-producer-api.md) | 素材提供者 IF と素材集の形式 |
| [channel-review-policy.md](docs/channel-review-policy.md) | 審査基準 |
| [stakeholders.md](docs/stakeholders.md) | 関係者の呼び方 |
