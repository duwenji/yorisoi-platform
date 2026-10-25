# yorisoi-platform

付き添いアプリのエコシステム **yorisoi**（寄り添い）の共通パッケージです。場所と状況に応じて AI が語りかけるアプリ（付き添いアプリ）が共通に使う部品を置いています。最初のアプリは [SanpoGuide](https://github.com/duwenji/yorisoi-sanpoguide)、管理システムは [yorisoi-console](https://github.com/duwenji/yorisoi-console) です。

方針は [ADR-ECOSYSTEM-PLATFORM](docs/decisions/ADR-ECOSYSTEM-PLATFORM.md) にあります。

## モジュール

| モジュール | 種類 | 中身 |
|---|---|---|
| `core-format` | JVM | チャンネル・素材集の形式（API-CHANNEL-PACKAGE・API-MATERIAL-PRODUCER）、その確認と署名、承認済みチャンネル・リスト（API-CHANNEL-LIST）、エコシステム共通の語彙。使う側で org.json が要る（Android には入っている） |
| `core-prompt` | JVM | プロンプトのテンプレート（Mustache の一部） |
| `kit-companion` | JVM | いま何を話すかの判定のエンジン（ルールの並びとゲート）、開発者モードの記録（`SessionRecorder`。使う側で org.json が要る） |
| `kit-channel` | JVM | アプリの中のチャンネル（利用者の変更を重ねた設定値とスロット）、チャンネルの一覧と使っているチャンネル、組み込みのチャンネルの読み込み、第三者のチャンネルの素材。第三者のチャンネルの取り込み（`remote`: `ChannelSync`・`ThirdPartyChannels`）、前もって取る素材の計画（`material`: `MaterialPlan`・`SelectorPlan`）、チャンネルの通報（`report`）。使う側で org.json が要る（`OkHttpProviderHttp`・`TinkEd25519` を使うなら OkHttp・Tink も） |
| `kit-voice` | Android | 読み上げ（オーディオフォーカス）と背景音の合成 |
| `kit-llm` | Android | AI の接続（Claude・OpenAI 互換・運営の AI の中継）と鍵の暗号化、自分のキーの 1 日の呼び出しの数、使わない返事（連絡先を含む）の判定 |
| [`kit-geo`](kit-geo/README.md) | Android | スポット・ルート・写真・天気・地図。スポット検索のキャッシュ |
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

形式や語彙を変えたときは、管理システムとアプリへの影響を契約書（[`docs/contracts/`](docs/contracts/README.md)）で確かめてから上げてください。

## 文書

エコシステム全体の文書（ADR・契約書・審査基準・関係者）は [docs/](docs/README.md) にあります。一覧、置き場所の決まり、文書の番号の呼び方もそこにあります。
