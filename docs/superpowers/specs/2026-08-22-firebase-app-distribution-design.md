# Firebase App Distribution 配信ワークフロー 設計

## 目的

自分用に OneMoreCoffee を配布するため、CI から署名済みの release APK を Firebase App Distribution へ配信する。
ストア配布は予定しないため、配信対象は `blue.starry.onemorecoffee` (release ビルドタイプ) の 1 種類のみとする。

参考実装は [SlashNephy/mitsubachi](https://github.com/SlashNephy/mitsubachi) の `deploy.yml` および `app/build.gradle.kts`。

## 前提と制約

- 本リポジトリは公開リポジトリである。keystore、各種パスワード、サービスアカウント鍵、Maps API キーはすべて GitHub Secrets 経由で扱い、リポジトリにも実行ログにも残さない。
- mitsubachi は `productFlavors` で production / staging / local を切り分けており、`deploy.yml` (staging) と `release.yml` (production) の 2 本立てになっている。本リポジトリには flavor が存在せず配信対象が 1 つのため、ワークフローは 1 本に集約する。
- release ビルドの署名は `keystore.properties` が存在する場合にのみ有効になる既存実装 (`app/build.gradle.kts`) をそのまま利用する。CI では同ファイルを Secrets から生成する。
- `secrets.properties` が無い場合、`MAPS_API_KEY` は `"DEFAULT_API_KEY"` にフォールバックする。CI で注入しないと配信物の地図が機能しないため、注入は必須とする。

## 決定事項

| 論点 | 決定 | 理由 |
| --- | --- | --- |
| Firebase の導入範囲 | App Distribution Gradle プラグインのみ | 配信が目的であり、Crashlytics / Analytics / App Check は不要。`appId` を Gradle プロパティで注入することで `google-services.json` と `google-services` プラグインを持ち込まずに済む |
| versionCode | `-PversionCode` で `github.run_number` を注入 (未指定時 1) | App Distribution のテスター更新通知は versionCode の増加で判定されるため単調増加が必要 |
| versionName | `app/build.gradle.kts` に据え置き (`0.1.0`) | 表示用ラベルに過ぎず、配信の識別は versionCode とリリースノートで足りる。今回使わない注入経路を先に作らない |
| 配信先 | `groups = "tester"` | 宛先の増減が Firebase コンソール側で完結し、公開リポジトリにメールアドレスを書かずに済む |
| トリガー | `workflow_run` (CI が main で成功完了) + `workflow_dispatch` | mitsubachi 準拠。CI が落ちたコミットを配信しない |
| ワークフロー本数 | 1 本 (`deploy.yml`) | 配信対象が 1 つのため。GitHub Release 契機の配信は作らない |
| GitHub Environment | `release` | Secrets のスコープを配信ジョブに限定する |

## 変更内容

### 1. `gradle/libs.versions.toml`

App Distribution Gradle プラグインを Renovate が追従できる形で追加する。

- `[versions]` に `firebase-app-distribution-gradle-plugin`
- `[plugins]` に `firebase-app-distribution = { id = "com.google.firebase.appdistribution", ... }`

### 2. `build.gradle.kts` (ルート)

既存の記述順・書式に合わせ、`alias(libs.plugins.firebase.app.distribution) apply false` を追加する。

### 3. `app/build.gradle.kts`

- `plugins` ブロックに `alias(libs.plugins.firebase.app.distribution)` を追加
- `defaultConfig.versionCode` を Gradle プロパティ `versionCode` から解決する形に変更 (未指定時は `1`)
- release ビルドタイプに App Distribution 設定を追加
  - `artifactType = "APK"` (ストア配布しないため AAB にしない)
  - `appId` は Gradle プロパティ `firebaseAppId` から注入
  - `serviceCredentialsFile = "$rootDir/firebase-service-account.json"`
  - `groups = "tester"`

設定ブロックの記述位置 (buildType 内の `firebaseAppDistribution {}` か `firebaseAppDistributionDefault {}` か) は実装時に検証して確定する。

### 4. `.github/workflows/deploy.yml` (新規)

```yaml
on:
  workflow_run:
    workflows: [CI]
    branches: [main]
    types: [completed]
  workflow_dispatch:
```

トップレベル `permissions: { }`、ジョブに `permissions: contents: read` と `environment: release` を付与する。
action は既存 `ci.yml` と同じく commit SHA ピン留め + バージョンコメントで揃える。

ジョブ `firebase-app-distribution` の手順:

1. `actions/checkout` / `jdx/mise-action` / `gradle/actions/setup-gradle` (`cache-encryption-key`) / `android-actions/setup-android`
2. `FIREBASE_SERVICE_ACCOUNT_JSON` から `firebase-service-account.json` を生成
3. `ANDROID_KEYSTORE` (base64) をデコードして `local.jks` を生成
4. `keystore.properties` を生成 (`android_keystore_path` は `${{ github.workspace }}/local.jks`)
5. `secrets.properties` に `MAPS_API_KEY` を書き込み
6. `git log -1 --pretty=short > release_notes.txt`
7. `./gradlew assembleRelease appDistributionUploadRelease --releaseNotesFile <path> -PversionCode=<run_number> -PfirebaseAppId=<secret>`

Secrets の値はすべて `env:` 経由で渡し、`run:` 内に直接展開しない。

### 5. `.gitignore`

`firebase-service-account.json` を追加する。CI が生成するのみだが、ローカル検証時の誤コミットを防ぐ。
`local.jks` は既存の `*.jks` でカバー済み。

## 必要な Secrets (Environment `release`)

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE` | `OneMoreCoffee.jks` を base64 エンコードしたもの |
| `ANDROID_KEYSTORE_PASSWORD` | keystore のパスワード |
| `ANDROID_KEYSTORE_ALIAS` | 鍵エイリアス |
| `ANDROID_KEYSTORE_ALIAS_PASSWORD` | 鍵エイリアスのパスワード |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | App Distribution 管理者権限を持つサービスアカウント鍵 |
| `FIREBASE_APP_ID` | `1:xxx:android:yyy` 形式 |
| `MAPS_API_KEY` | Maps SDK の API キー |
| `GRADLE_ENCRYPTION_KEY` | 任意。`org.gradle.configuration-cache=true` のため、未設定だと configuration cache が保存されない |

## Firebase コンソール側の事前作業 (ユーザー実施)

1. Firebase プロジェクトの作成
2. Android アプリ (`blue.starry.onemorecoffee`) の登録
3. App Distribution の有効化
4. テスターグループ `tester` の作成と自分の追加
5. サービスアカウントの発行と鍵の取得

## 検証方針

CI 上でしか通せない経路があるため、段階を分ける。

1. **ローカル**: `./gradlew assembleRelease -PversionCode=999` を実行し、署名済み APK が生成されること、`aapt2 dump badging` で versionCode が 999 になっていることを確認する。App Distribution へのアップロードは認証情報が無いため行わない。
2. **ローカル**: 配信タスクが configuration cache と両立するかを確認する。非対応なら該当タスクの実行方法を調整する。
3. **CI**: main へのマージ後、`workflow_dispatch` で初回実行し、App Distribution にビルドが登録されテスターへ配信されることを確認する。
4. **実機**: 配信された APK をインストールし、地図が表示されること (= `MAPS_API_KEY` の注入が効いていること) を確認する。

## 実装時に検証が必要な不確実性

- `firebaseAppDistribution {}` ブロックが buildType 内で使用可能か。使用できない場合は `firebaseAppDistributionDefault {}` へ寄せる。
- App Distribution Gradle プラグインが configuration cache に対応しているか。

## スコープ外

- Crashlytics / Analytics / App Check の導入
- GitHub Release 契機の配信ワークフロー
- AAB での配信 (ストア配布を予定しないため)
- product flavor の導入
