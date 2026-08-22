# Firebase App Distribution 配信ワークフロー 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CI から署名済み release APK を Firebase App Distribution へ配信できるようにする。

**Architecture:** App Distribution Gradle プラグインのみを導入し、`appId` と `versionCode` を Gradle プロパティで CI から注入する。`google-services.json` は持ち込まない。署名情報・サービスアカウント鍵・Maps API キーは GitHub Secrets からワークフロー内でファイルに復元する。配信ワークフローは `workflow_run` (CI が main で成功) と `workflow_dispatch` の 2 トリガーで動く 1 本に集約する。

**Tech Stack:** Gradle 9.7.0 (Kotlin DSL), AGP 9.3.1, Firebase App Distribution Gradle Plugin 5.3.0, GitHub Actions

設計: `docs/superpowers/specs/2026-08-22-firebase-app-distribution-design.md`
参考実装: [SlashNephy/mitsubachi](https://github.com/SlashNephy/mitsubachi) の `.github/workflows/deploy.yml` と `app/build.gradle.kts`

## Global Constraints

- 本リポジトリは **公開リポジトリ**。秘密情報をコードにもコミットログにも実行ログにも残さない。Secrets は必ず `env:` 経由で渡し、`run:` の文字列に直接展開しない。
- GitHub Actions は **commit SHA でピン留めし、末尾に `# vX.Y.Z` 形式のバージョンコメント**を付ける (Renovate が追従するため)。既存の `.github/workflows/ci.yml` と同じ SHA・同じバージョンを使う。
- ワークフローはトップレベルで `permissions: { }` を宣言し、ジョブ単位で最小権限を付与する。
- 依存は `gradle/libs.versions.toml` で管理する。curl や npx による直接インストールは行わない。
- コード内のコメントは日本語で書く。ログ・エラーメッセージは英語。
- コミットメッセージは Conventional Commits 形式。`Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>` を付与する。
- 配信対象は release ビルドタイプのみ。product flavor は追加しない。
- インデントは Kotlin DSL・YAML ともに既存ファイルに合わせる (`app/build.gradle.kts` は 4 スペース、ワークフローは 2 スペース)。

---

### Task 1: versionCode を Gradle プロパティから注入できるようにする

CI が `github.run_number` を versionCode として渡せるようにする。App Distribution のテスター更新通知は versionCode の増加で判定されるため、これが無いと 2 回目以降の配信がアップデートとして認識されない。

**Files:**
- Modify: `app/build.gradle.kts` (`defaultConfig` ブロック内の `versionCode = 1`)

**Interfaces:**
- Consumes: なし
- Produces: Gradle プロパティ `versionCode` (String)。未指定時および数値に変換できない場合は `1` にフォールバックする。Task 3 のワークフローが `-PversionCode=` で渡す。

- [ ] **Step 1: 変更前の versionCode を記録する**

証跡として before を取る。

```bash
./gradlew :app:assembleDebug
```

続いて生成された APK のバージョンを確認する。`aapt2` は Android SDK の build-tools 配下にある。

```bash
find "$ANDROID_HOME" -name aapt2 -path '*build-tools*' | sort -V | tail -1
```

見つかったパスを使って以下を実行し、出力に `versionCode='1'` が含まれることを確認する。

```bash
"$(find "$ANDROID_HOME" -name aapt2 -path '*build-tools*' | sort -V | tail -1)" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

期待される出力の例:

```
package: name='blue.starry.onemorecoffee.debug' versionCode='1' versionName='0.1.0' ...
```

- [ ] **Step 2: versionCode をプロパティ解決に変更する**

`app/build.gradle.kts` の `defaultConfig` ブロック内、以下の行を

```kotlin
        versionCode = 1
```

次に置き換える。

```kotlin
        // CI からは -PversionCode=<github.run_number> で注入する
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
```

`versionName = "0.1.0"` はそのまま変更しない。

- [ ] **Step 3: プロパティ未指定時のフォールバックを確認する**

```bash
./gradlew :app:assembleDebug
```

```bash
"$(find "$ANDROID_HOME" -name aapt2 -path '*build-tools*' | sort -V | tail -1)" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

期待: `versionCode='1'` のまま (変更前と同じ)。

- [ ] **Step 4: プロパティ指定時に反映されることを確認する**

```bash
./gradlew :app:assembleDebug -PversionCode=999
```

```bash
"$(find "$ANDROID_HOME" -name aapt2 -path '*build-tools*' | sort -V | tail -1)" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

期待: `versionCode='999'`。ここが `1` のままなら、`findProperty` の戻り値がキャストで落ちている。その場合は `project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1` を試す。

- [ ] **Step 5: コミット**

```bash
git add app/build.gradle.kts
git commit -m "$(cat <<'EOF'
feat: versionCode を Gradle プロパティから注入できるようにする

Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: App Distribution Gradle プラグインを導入する

配信タスク `appDistributionUploadRelease` を生やす。`appId` を注入する構成にすることで `google-services.json` と `google-services` プラグインを不要にする。

**Files:**
- Modify: `gradle/libs.versions.toml` (`[versions]` と `[plugins]`)
- Modify: `build.gradle.kts` (ルートの `plugins` ブロック)
- Modify: `app/build.gradle.kts` (import、`plugins` ブロック、`buildTypes.release`)
- Modify: `.gitignore`

**Interfaces:**
- Consumes: Task 1 の Gradle プロパティ `versionCode`
- Produces:
  - Gradle タスク `appDistributionUploadRelease`
  - Gradle プロパティ `firebaseAppId` (String)。未指定時は空文字。Task 3 のワークフローが `-PfirebaseAppId=` で渡す。
  - サービスアカウント鍵の期待パス `<rootDir>/firebase-service-account.json`。Task 3 のワークフローがこのパスに生成する。

- [ ] **Step 1: バージョンカタログにプラグインを追加する**

`gradle/libs.versions.toml` の `[versions]` ブロック、`mapsplatform-secrets = "2.0.1"` の直後に追加する。

```toml
firebase-app-distribution-gradle-plugin = "5.3.0"
```

同ファイルの `[plugins]` ブロック末尾、`mapsplatform-secrets = ...` の行の後に追加する。

```toml
firebase-app-distribution = { id = "com.google.firebase.appdistribution", version.ref = "firebase-app-distribution-gradle-plugin" }
```

- [ ] **Step 2: ルートの build.gradle.kts でプラグインを宣言する**

`build.gradle.kts` の `plugins` ブロック、`alias(libs.plugins.mapsplatform.secrets) apply false` の後に追加する。

```kotlin
    alias(libs.plugins.firebase.app.distribution) apply false
```

- [ ] **Step 3: バージョンカタログの解決を確認する**

```bash
./gradlew help
```

期待: `BUILD SUCCESSFUL`。プラグインが解決できない場合はここで失敗するので、先に切り分ける。

- [ ] **Step 4: app モジュールにプラグインを適用する**

`app/build.gradle.kts` の `plugins` ブロック、`alias(libs.plugins.mapsplatform.secrets)` の後に追加する。

```kotlin
    alias(libs.plugins.firebase.app.distribution)
```

- [ ] **Step 5: 配信タスクが生成されることを確認する**

```bash
./gradlew :app:tasks --all | grep -i appDistribution
```

期待: `appDistributionUploadRelease` を含む複数のタスクが列挙される。

- [ ] **Step 6: release ビルドタイプに配信設定を追加する**

`app/build.gradle.kts` の先頭、既存の `import java.util.Properties` の**前**に import を追加する (アルファベット順)。

```kotlin
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties
```

続いて `buildTypes` ブロック内の `release { ... }` に配信設定を追加する。変更後の `release` ブロックは次のようになる。

```kotlin
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("default")
            }
            firebaseAppDistribution {
                // Play ストアでの配布を予定しないため APK で配信する
                artifactType = "APK"
                // 公開リポジトリのため appId は CI から -PfirebaseAppId で注入する
                appId = project.findProperty("firebaseAppId") as? String ?: ""
                serviceCredentialsFile = "$rootDir/firebase-service-account.json"
                groups = "tester"
            }
        }
```

**この記法が通らない場合の代替:** `firebaseAppDistribution` が buildType 内で解決できないときは、`android` ブロック直下の `firebaseAppDistributionDefault { ... }` に同じ内容を移す (参考実装の mitsubachi はこちらを使っている)。その場合 import は不要になる。どちらを採用したかは Step 8 の結果とともに記録する。

- [ ] **Step 7: configuration cache と両立するか確認する**

```bash
./gradlew :app:tasks --all -PfirebaseAppId=1:000000000000:android:0000000000000000
```

期待: `BUILD SUCCESSFUL`。`Configuration cache problems found` が出た場合は、その内容を記録した上でユーザーに報告する。設定を勝手に無効化しない。

- [ ] **Step 8: release ビルドが通ることを確認する**

`keystore.properties` がローカルに存在するため署名済み APK が生成される。

```bash
./gradlew :app:assembleRelease -PversionCode=999 -PfirebaseAppId=1:000000000000:android:0000000000000000
```

期待: `BUILD SUCCESSFUL`。続いて生成物を確認する。

```bash
"$(find "$ANDROID_HOME" -name aapt2 -path '*build-tools*' | sort -V | tail -1)" dump badging app/build/outputs/apk/release/app-release.apk | head -1
```

期待: `package: name='blue.starry.onemorecoffee' versionCode='999' versionName='0.1.0'`。`applicationId` に `.debug` が付いていないことを確認する。

署名も確認する。

```bash
"$(dirname "$(find "$ANDROID_HOME" -name apksigner -path '*build-tools*' | sort -V | tail -1)")"/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

期待: 証明書情報が表示され、エラーが出ない。

- [ ] **Step 9: サービスアカウント鍵を gitignore する**

`.gitignore` の `keystore.properties` の行の後に追加する。

```
firebase-service-account.json
```

`local.jks` は既存の `*.jks` でカバーされているため追記不要。

- [ ] **Step 10: 秘密情報が混入していないことを確認する**

```bash
git status --short
git diff
```

期待: 変更ファイルは `gradle/libs.versions.toml` / `build.gradle.kts` / `app/build.gradle.kts` / `.gitignore` の 4 つのみ。差分に実在の appId・鍵・パスワードが含まれていないこと (Step 7-8 で使ったダミー appId はコードに残さない)。

- [ ] **Step 11: コミット**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts .gitignore
git commit -m "$(cat <<'EOF'
feat: Firebase App Distribution Gradle プラグインを導入

Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 配信ワークフローを追加する

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: Task 1 の `versionCode`、Task 2 の `firebaseAppId` / `appDistributionUploadRelease` / `<rootDir>/firebase-service-account.json`
- Produces: GitHub Environment `release` に依存する配信ジョブ

- [ ] **Step 1: 既存ワークフローの action バージョンを確認する**

新規ワークフローは既存と同じ SHA を使う。ずれると Renovate の更新が分散する。

```bash
grep -oE 'uses: [^ ]+' .github/workflows/ci.yml | sort -u
```

以下が現時点の値。Step 2 のファイル内容と一致していることを確認する。

- `actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6.1.0`
- `jdx/mise-action@3c2e0cf82a5b2e5249f0d3635a4d83d0ae861518 # v4.2.5`
- `gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0`
- `android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4.0.1`

もし上記と異なっていた場合は、`ci.yml` の実際の値を優先する。

- [ ] **Step 2: deploy.yml を作成する**

`.github/workflows/deploy.yml` に以下を書く。

```yaml
name: Deploy

on:
  workflow_run:
    workflows:
      - CI
    branches:
      - main
    types:
      - completed
  workflow_dispatch:

permissions: { }

jobs:
  firebase-app-distribution:
    if: github.event_name == 'workflow_dispatch' || github.event.workflow_run.conclusion == 'success'
    environment: release
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6.1.0
      - uses: jdx/mise-action@3c2e0cf82a5b2e5249f0d3635a4d83d0ae861518 # v4.2.5
      - uses: gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0
        with:
          cache-encryption-key: ${{ secrets.GRADLE_ENCRYPTION_KEY }}
      - uses: android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4.0.1
      - run: echo "$FIREBASE_SERVICE_ACCOUNT_JSON" > firebase-service-account.json
        env:
          FIREBASE_SERVICE_ACCOUNT_JSON: ${{ secrets.FIREBASE_SERVICE_ACCOUNT_JSON }}
      - run: echo "$ANDROID_KEYSTORE" | base64 --decode > local.jks
        env:
          ANDROID_KEYSTORE: ${{ secrets.ANDROID_KEYSTORE }}
      - run: |
          cat >> secrets.properties << EOF
          MAPS_API_KEY=$MAPS_API_KEY
          EOF
        env:
          MAPS_API_KEY: ${{ secrets.MAPS_API_KEY }}
      - run: |
          cat >> keystore.properties << EOF
          android_keystore_path=$ANDROID_KEYSTORE_PATH
          android_keystore_password=$ANDROID_KEYSTORE_PASSWORD
          android_keystore_alias=$ANDROID_KEYSTORE_ALIAS
          android_keystore_alias_password=$ANDROID_KEYSTORE_ALIAS_PASSWORD
          EOF
        env:
          ANDROID_KEYSTORE_PATH: ${{ github.workspace }}/local.jks
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEYSTORE_ALIAS: ${{ secrets.ANDROID_KEYSTORE_ALIAS }}
          ANDROID_KEYSTORE_ALIAS_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_ALIAS_PASSWORD }}
      - run: git log -1 --pretty=short > release_notes.txt
      - run: ./gradlew assembleRelease appDistributionUploadRelease --releaseNotesFile "$RELEASE_NOTES_FILE" -PversionCode="$VERSION_CODE" -PfirebaseAppId="$FIREBASE_APP_ID"
        env:
          RELEASE_NOTES_FILE: ${{ github.workspace }}/release_notes.txt
          VERSION_CODE: ${{ github.run_number }}
          FIREBASE_APP_ID: ${{ secrets.FIREBASE_APP_ID }}
```

参考実装との差分は次の 3 点。いずれも意図的なもの。

1. flavor が無いためタスク名は `assembleRelease` / `appDistributionUploadRelease`
2. `google-services.json` を生成しない (Task 2 で `appId` 注入方式にしたため)
3. `if` 条件に `github.event_name == 'workflow_dispatch'` を追加。参考実装は `workflow_dispatch` 時に `github.event.workflow_run` が存在せず条件が偽になり、手動実行してもジョブがスキップされる。

- [ ] **Step 3: YAML の構文を検証する**

`actionlint` があれば使う。

```bash
command -v actionlint && actionlint .github/workflows/deploy.yml
```

見つからない場合は YAML としての妥当性だけ確認する。

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/deploy.yml')); print('OK')"
```

期待: `OK`。

- [ ] **Step 4: Secrets の参照名を洗い出す**

```bash
grep -oE 'secrets\.[A-Z_]+' .github/workflows/deploy.yml | sort -u
```

期待される 8 件。この一覧をそのまま PR 本文に載せる。

```
secrets.ANDROID_KEYSTORE
secrets.ANDROID_KEYSTORE_ALIAS
secrets.ANDROID_KEYSTORE_ALIAS_PASSWORD
secrets.ANDROID_KEYSTORE_PASSWORD
secrets.FIREBASE_APP_ID
secrets.FIREBASE_SERVICE_ACCOUNT_JSON
secrets.GRADLE_ENCRYPTION_KEY
secrets.MAPS_API_KEY
```

- [ ] **Step 5: 秘密情報の直接展開が無いことを確認する**

`run:` の中で `${{ secrets.* }}` を直接使っていないこと (ログへの漏洩とシェルインジェクションの両方を防ぐ)。

```bash
grep -nE 'run:.*secrets\.' .github/workflows/deploy.yml
```

期待: 何も出力されない。

- [ ] **Step 6: コミット**

```bash
git add .github/workflows/deploy.yml
git commit -m "$(cat <<'EOF'
feat: Firebase App Distribution へ配信するワークフローを追加

Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: PR を作成する

**Files:** なし (Git 操作のみ)

- [ ] **Step 1: 差分全体を確認する**

```bash
git diff main...HEAD
```

秘密情報が含まれていないこと、`gradle.properties` の未コミット変更 (本作業のスコープ外) が混入していないことを確認する。

- [ ] **Step 2: プッシュ**

```bash
git push -u origin feat/app-distribution
```

- [ ] **Step 3: Draft PR を作成する**

CI 上での配信は main マージ後にしか検証できず、Secrets の登録と Firebase コンソール側の作業もユーザー側に残るため、**Draft PR** で作成する。

本文には次を含める。

- 変更概要
- ユーザーが実施する必要がある作業 (Firebase プロジェクト作成、Android アプリ登録、App Distribution 有効化、`tester` グループ作成、サービスアカウント発行、GitHub Environment `release` の作成と Secrets 登録)
- Task 3 Step 4 で洗い出した Secrets 一覧
- ローカル検証の証跡 (Task 1 Step 4、Task 2 Step 8 のコマンドと出力)
- 未検証事項 (CI 上での実配信、配信 APK の実機動作)

- [ ] **Step 4: 自分を Assign する**

```bash
gh pr edit --add-assignee SlashNephy
```

- [ ] **Step 5: マージ可否を確認する**

```bash
gh pr view --json mergeable,mergeStateStatus
```

`CONFLICTING` の場合は main を取り込んで解消する。

---

### Task 5: CI 上での配信を検証する (Secrets 登録後)

このタスクは、ユーザーが Firebase コンソール側の作業と Secrets 登録を完了してから実施する。それまで PR は Draft のまま維持する。

**Files:** なし

- [ ] **Step 1: Secrets が登録されたことを確認する**

```bash
gh secret list --env release
```

期待: Task 3 Step 4 の 8 件が並ぶ (`GRADLE_ENCRYPTION_KEY` は任意)。

- [ ] **Step 2: PR を Ready にして main へマージする**

`workflow_run` トリガーはデフォルトブランチ上の定義でしか発火しないため、マージが必要。

- [ ] **Step 3: 手動実行する**

```bash
gh workflow run deploy.yml --ref main
```

- [ ] **Step 4: 実行結果を確認する**

```bash
gh run list --workflow deploy.yml --limit 1
```

失敗した場合はログを取得する。

```bash
gh run view --log-failed
```

- [ ] **Step 5: 配信を確認する**

Firebase コンソールの App Distribution で、`versionCode` が実行時の run number と一致するビルドが登録され、`tester` グループへ配信されていることを確認する。スクリーンショットを証跡として取得する。

- [ ] **Step 6: 実機で動作確認する**

配信された APK をインストールし、地図画面が表示されること (= `MAPS_API_KEY` の注入が効いていること) を mobile-mcp で確認する。`"DEFAULT_API_KEY"` のままだと地図がグレー表示になるため、ここが実質的な検証ポイントになる。スクリーンショットを証跡として取得する。

---

## リスクと留意点

- **`workflow_run` はデフォルトブランチの定義で動く。** PR 上で `deploy.yml` を編集しても発火しない。初回検証は必ず main マージ後になる。
- **`appId` が空文字のまま配信タスクを実行すると失敗する。** ローカルで誤って `appDistributionUploadRelease` を叩かないよう注意する (認証情報も無いためどのみち失敗する)。
- **`secrets.properties` は `>>` で追記している。** チェックアウト直後は存在しないため実質新規作成だが、参考実装に合わせている。
- **`GRADLE_ENCRYPTION_KEY` 未設定でもワークフローは動く。** configuration cache が保存されず毎回コールドになるだけ。
