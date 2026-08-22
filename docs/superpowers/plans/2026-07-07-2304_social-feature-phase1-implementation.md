# OneMoreCoffee ソーシャル機能フェーズ 1 (MVP) 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OneMoreCoffee に「リーグ」（招待コード制グループ）を追加し、フレンド間の訪問アクティビティフィードと総制覇数ランキングを Firebase (Firestore + Anonymous Auth) で実現する。

**Architecture:** 端末の Room が唯一の原本で、サーバー（Firestore）には導出データのみを置く。Firebase 依存は新設の `core/social` に隔離し、`core/domain` のインターフェース経由で `core/data`（インポート時の publish フック）と `feature/friends`（UI）が利用する。ランキングはサーバー計算なし（各自が統計を自己申告し、クライアントが並べ替える）。

**Tech Stack:** Kotlin 2.3.21 / AGP 9.2.1 / Jetpack Compose (BOM alpha) / Hilt / Room / Firebase Firestore + Auth (BoM) / DataStore Preferences / JUnit4 + Truth + Robolectric

**設計書:** `docs/superpowers/specs/2026-07-07-social-feature-design.md`（以下「設計書」）

## Global Constraints

- このリポジトリ（OneMoreCoffee）のルートで作業する
- 作業ブランチ: `feature/social-league-phase1`（Task 0 で作成。main に直接コミットしない）
- compileSdk = 36、minSdk = 26、namespace は `blue.starry.onemorecoffee.<モジュール名>` 形式
- 依存追加は必ず `gradle/libs.versions.toml`（バージョンカタログ）経由。Renovate が識別できる形式を維持する
- モジュール参照は typesafe accessor（`projects.core.social` 等）を使う
- テスト: JUnit4 + Truth（`assertThat`）。コルーチンは `kotlinx-coroutines-test`、Android 依存があるテストのみ Robolectric
- UI 文言は日本語ハードコード（既存の `StatsScreen` / `App.kt` の流儀）
- コミットメッセージ: Conventional Commits 形式・日本語。フッターに `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`
- Firebase は Spark（無料）プランの範囲のみ使用。Cloud Functions と google-services Gradle プラグインは使わない（Firebase 初期化は secrets.properties 経由の手動初期化）
- Firestore 書き込みのうちユーザー操作の応答が必要なもの（リーグ作成・参加・退出・統計更新）は `await()` する。訪問イベントの publish は `await()` しない（オフライン時も Firestore のローカルキューに積まれ、再接続時に自動送信される）

## 設計書からの変更点（コードの現実に合わせた確定事項）

計画作成時にコードベースを調査した結果、設計書の次の想定を現実に合わせて変更する。実装者はこの表を設計書より優先すること。

| 設計書の記述 | 実装の現実 | 本計画での確定 |
|---|---|---|
| VISIT イベントは完全なタイムスタンプ `visitedAt` を持つ（§4.5） | Room の `VisitEntity.visitedOn` は `LocalDate`（時刻は存在しない） | イベントは `visitedOn`（日付）+ `createdAt`（投稿時刻、serverTimestamp）を持つ。フィードの相対時刻表示は `createdAt` 基準（即時投稿なので実質リアルタイム） |
| publish は WorkManager 経由（§5.4） | WorkManager 依存は存在しない | Firestore のオフライン永続化 + 書き込みキューで再送を実現。WorkManager は追加しない |
| 5 タブ構成（§5.1） | ボトムタブは 3 つ（マップ/リスト/統計）で設定はトップバーのアイコン | フレンドをボトム 4 つ目のタブとして追加。設定はトップバーのまま |
| 訪問保存（手動 or インポート）から publish（§5.4） | 訪問の書き込み経路は現状インポートのみ（手動記録は未実装） | publish フックは `VisitRepositoryImpl.importStarbucksVisits` に設置。将来手動記録が実装されたら同じ `SocialRepository.publishFirstVisits` を呼ぶ |
| google-services.json を gitignore して CI 注入（§5.7） | リポジトリは secrets-gradle-plugin + `secrets.properties` で API キーを管理済み | `FIREBASE_PROJECT_ID` / `FIREBASE_APPLICATION_ID` / `FIREBASE_API_KEY` を secrets.properties に置き、`FirebaseOptions` を手動構築（AGP 9 と google-services プラグインの互換問題も回避） |
| Emulator Suite で Repository 統合テスト（§5.8） | Robolectric + Firestore SDK (gRPC) の組合せは安定性に懸念 | フェーズ 1 ではマッパー等の純粋ロジックを unit test し、Firestore 統合は手動 E2E（Task 12）で確認。自動化はオープンクエスチョンとして残す |

---

### Task 0: 事前準備（人間の作業を含む）

**Files:** なし（コード変更なし）

**Interfaces:**
- Produces: Firebase プロジェクトの 3 値（後続 Task 2 が `secrets.properties` で消費）、作業ブランチ

- [ ] **Step 1: Firebase プロジェクトを作成する（人間の作業）**

実装エージェントはここで停止してユーザーに依頼する。ユーザーが行うこと:

1. https://console.firebase.google.com で新規プロジェクト作成（名前例: `onemorecoffee`、Analytics 不要）
2. Android アプリを 2 つ追加: パッケージ名 `blue.starry.onemorecoffee` と `blue.starry.onemorecoffee.debug`（debug ビルドは applicationIdSuffix `.debug` のため）
3. Build > Firestore Database でデータベースを作成（ロケーション `asia-northeast1`、本番モード）
4. Build > Authentication > Sign-in method で「匿名」を有効化
5. プロジェクト設定から次の 3 値を控える: プロジェクト ID、アプリ ID（`1:xxxx:android:xxxx` 形式、debug 側でよい）、ウェブ API キー

- [ ] **Step 2: 作業ブランチを作成する**

```bash
cd ~/ghq/github.com/SlashNephy/OneMoreCoffee
git switch main && git pull
git switch -c feature/social-league-phase1
```

Expected: `Switched to a new branch 'feature/social-league-phase1'`

- [ ] **Step 3: secrets.properties に Firebase の 3 値を記入する（人間の作業、コミットしない）**

`~/ghq/github.com/SlashNephy/OneMoreCoffee/secrets.properties`（gitignore 済み）に追記:

```properties
FIREBASE_PROJECT_ID=<Step 1 のプロジェクト ID>
FIREBASE_APPLICATION_ID=<Step 1 のアプリ ID>
FIREBASE_API_KEY=<Step 1 のウェブ API キー>
```

---

### Task 1: モジュール骨格の追加（:core:social / :feature:friends）

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `core/social/build.gradle.kts`
- Create: `core/social/src/main/AndroidManifest.xml`（不要。AGP 9 はマニフェスト省略可、作らない）
- Create: `feature/friends/build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: 空の 2 モジュール `:core:social` / `:feature:friends`（後続タスクがソースを配置）、カタログエントリ `libs.firebase.*` / `libs.androidx.datastore.preferences` / `libs.kotlinx.coroutines.play.services`

- [ ] **Step 1: バージョンカタログに依存を追加する**

`gradle/libs.versions.toml` の `[versions]` に追加（アルファベット順は既存に倣い、`javax-inject` の前後に配置）:

```toml
firebase-bom = "34.0.0"
androidx-datastore = "1.1.7"
```

注意: `firebase-bom` と `androidx-datastore` は計画作成時点の版。実装時に最新安定版へ更新してよい（Renovate が追従する）。

`[libraries]` に追加:

```toml
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
firebase-firestore = { module = "com.google.firebase:firebase-firestore" }
firebase-auth = { module = "com.google.firebase:firebase-auth" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "androidx-datastore" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "kotlinx-coroutines" }
```

- [ ] **Step 2: settings.gradle.kts にモジュールを登録する**

`include(...)` ブロックを次のように変更（`:core:ui` の後に `:core:social`、`:feature:import` の後に `:feature:friends`）:

```kotlin
include(
    ":app",
    ":core:common",
    ":core:domain",
    ":core:data",
    ":core:ui",
    ":core:social",
    ":feature:map",
    ":feature:list",
    ":feature:stats",
    ":feature:settings",
    ":feature:import",
    ":feature:friends",
)
```

- [ ] **Step 3: core/social/build.gradle.kts を作成する**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "blue.starry.onemorecoffee.core.social"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(projects.core.domain)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
```

- [ ] **Step 4: feature/friends/build.gradle.kts を作成する**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "blue.starry.onemorecoffee.feature.friends"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
}
```

- [ ] **Step 5: app/build.gradle.kts にモジュール依存を追加する**

`dependencies` ブロックの `implementation(projects.feature.import)` の後に追加:

```kotlin
    implementation(projects.core.social)
    implementation(projects.feature.friends)
```

- [ ] **Step 6: ビルドが通ることを確認する**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`（空モジュールでもマニフェストなしで AGP が既定を生成する。失敗する場合のみ `core/social/src/main/AndroidManifest.xml` に `<manifest />` を作成）

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts core/social feature/friends app/build.gradle.kts
git commit -m "build: core:social と feature:friends モジュールの骨格を追加"
```

---

### Task 2: Firebase 手動初期化と secrets 配線

**Files:**
- Modify: `secrets.defaults.properties`
- Modify: `app/build.gradle.kts`
- Create: `core/social/src/main/java/blue/starry/onemorecoffee/core/social/FirebaseInitializer.kt`
- Modify: `app/src/main/java/blue/starry/onemorecoffee/OneMoreCoffeeApplication.kt`

**Interfaces:**
- Consumes: Task 0 の secrets.properties、Task 1 のモジュール骨格
- Produces: `FirebaseInitializer.initialize(context, projectId, applicationId, apiKey)` / `FirebaseInitializer.isAvailable(context): Boolean`（Task 5 が使用）

- [ ] **Step 1: secrets.defaults.properties に既定値を追加する**

```properties
# app モジュール追加時は Secrets Gradle Plugin の defaultPropertiesFileName にこのファイル名を指定する。
MAPS_API_KEY=DEFAULT_API_KEY
FIREBASE_PROJECT_ID=DEFAULT_FIREBASE_PROJECT_ID
FIREBASE_APPLICATION_ID=DEFAULT_FIREBASE_APPLICATION_ID
FIREBASE_API_KEY=DEFAULT_FIREBASE_API_KEY
```

- [ ] **Step 2: app/build.gradle.kts に BuildConfig フィールドを追加する**

`val mapsApiKey = ...` の直後に追加:

```kotlin
fun secretProperty(name: String, defaultValue: String): String =
    loadRootProperties("secrets.properties").nonBlankProperty(name)
        ?: loadRootProperties("secrets.defaults.properties").nonBlankProperty(name)
        ?: defaultValue

val firebaseProjectId = secretProperty("FIREBASE_PROJECT_ID", "DEFAULT_FIREBASE_PROJECT_ID")
val firebaseApplicationId = secretProperty("FIREBASE_APPLICATION_ID", "DEFAULT_FIREBASE_APPLICATION_ID")
val firebaseApiKey = secretProperty("FIREBASE_API_KEY", "DEFAULT_FIREBASE_API_KEY")
```

`defaultConfig` の `buildConfigField("String", "MAPS_API_KEY", ...)` の直後に追加:

```kotlin
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseProjectId.asBuildConfigStringLiteral())
        buildConfigField("String", "FIREBASE_APPLICATION_ID", firebaseApplicationId.asBuildConfigStringLiteral())
        buildConfigField("String", "FIREBASE_API_KEY", firebaseApiKey.asBuildConfigStringLiteral())
```

`secrets { ignoreList.add("MAPS_API_KEY") }` ブロックに追加:

```kotlin
    ignoreList.add("FIREBASE_PROJECT_ID")
    ignoreList.add("FIREBASE_APPLICATION_ID")
    ignoreList.add("FIREBASE_API_KEY")
```

- [ ] **Step 3: FirebaseInitializer を実装する**

`core/social/src/main/java/blue/starry/onemorecoffee/core/social/FirebaseInitializer.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

// google-services Gradle プラグインを使わず、secrets.properties 由来の値で手動初期化する。
// 値が既定値のままなら初期化せず、ソーシャル機能は「未構成」として振る舞う（アプリ本体は動く）。
object FirebaseInitializer {
    fun initialize(context: Context, projectId: String, applicationId: String, apiKey: String) {
        if (projectId.startsWith("DEFAULT_") || applicationId.startsWith("DEFAULT_") || apiKey.startsWith("DEFAULT_")) {
            return
        }
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return
        }

        val options = FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .build()
        FirebaseApp.initializeApp(context, options)
    }

    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
```

- [ ] **Step 4: Application で初期化を呼ぶ**

`app/src/main/java/blue/starry/onemorecoffee/OneMoreCoffeeApplication.kt` を置き換え:

```kotlin
package blue.starry.onemorecoffee

import android.app.Application
import blue.starry.onemorecoffee.core.social.FirebaseInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OneMoreCoffeeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.initialize(
            context = this,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            applicationId = BuildConfig.FIREBASE_APPLICATION_ID,
            apiKey = BuildConfig.FIREBASE_API_KEY,
        )
    }
}
```

- [ ] **Step 5: ビルド確認と Commit**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

```bash
git add secrets.defaults.properties app/build.gradle.kts core/social app/src/main/java/blue/starry/onemorecoffee/OneMoreCoffeeApplication.kt
git commit -m "feat: secrets.properties 経由の Firebase 手動初期化を追加"
```

---

### Task 3: domain 層のモデルと SocialRepository インターフェース

**Files:**
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/SocialSession.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/SocialProfile.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/League.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/LeagueMember.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/ActivityEvent.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/SocialStats.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/FirstVisit.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/VisitPublicationPlan.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/repository/SocialRepository.kt`
- Test: `core/domain/src/test/java/blue/starry/onemorecoffee/core/domain/model/SocialStatsTest.kt`
- Test: `core/domain/src/test/java/blue/starry/onemorecoffee/core/domain/model/VisitPublicationPlanTest.kt`

**Interfaces:**
- Consumes: 既存の `StoreVisitSummary`（`isVisited`、`prefecture` プロパティ）
- Produces（後続の全タスクが参照する中核型。シグネチャ厳守）:
  - `SocialSession(uid: String, leagueId: String)`
  - `SocialProfile(displayName: String, emoji: String)`
  - `League(id: String, name: String, inviteCode: String, createdBy: String)`
  - `LeagueMember(uid: String, displayName: String, emoji: String, visitedStoreCount: Int, prefectureCount: Int, updatedAt: Instant?)`
  - `ActivityEvent`（sealed。`Visit` / `Backfill` / `MemberJoined`）
  - `SocialStats.from(summaries: List<StoreVisitSummary>): SocialStats`
  - `FirstVisit(storeId: String, visitedOn: LocalDate)`
  - `VisitPublicationPlan.of(firstVisits: List<FirstVisit>): VisitPublicationPlan`
  - `SocialRepository`（下記全メソッド）と `SocialUnavailableException`

- [ ] **Step 1: 失敗するテストを書く（SocialStats）**

`core/domain/src/test/java/blue/starry/onemorecoffee/core/domain/model/SocialStatsTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class SocialStatsTest {
    @Test
    fun from_countsVisitedStoresAndDistinctPrefectures() {
        val summaries = listOf(
            summary(id = "1", prefecture = "東京都", visitCount = 2),
            summary(id = "2", prefecture = "東京都", visitCount = 1),
            summary(id = "3", prefecture = "京都府", visitCount = 1),
            summary(id = "4", prefecture = "大阪府", visitCount = 0),
        )

        val stats = SocialStats.from(summaries)

        assertThat(stats.visitedStoreCount).isEqualTo(3)
        assertThat(stats.prefectureCount).isEqualTo(2)
    }

    @Test
    fun from_emptySummaries_returnsZero() {
        val stats = SocialStats.from(emptyList())

        assertThat(stats.visitedStoreCount).isEqualTo(0)
        assertThat(stats.prefectureCount).isEqualTo(0)
    }

    private fun summary(
        id: String,
        prefecture: String,
        visitCount: Int,
    ): StoreVisitSummary {
        return StoreVisitSummary(
            id = id,
            name = "スターバックス",
            prefecture = prefecture,
            fullAddress = "住所",
            latitude = 35.0,
            longitude = 139.0,
            isReserve = false,
            visitCount = visitCount,
            lastVisitedOn = if (visitCount > 0) LocalDate.of(2026, 7, 1) else null,
        )
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./gradlew :core:domain:test --tests "blue.starry.onemorecoffee.core.domain.model.SocialStatsTest"`
Expected: コンパイルエラー（`SocialStats` 未定義）で FAIL

- [ ] **Step 3: SocialStats を実装する**

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/SocialStats.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

data class SocialStats(
    val visitedStoreCount: Int,
    val prefectureCount: Int,
) {
    companion object {
        fun from(summaries: List<StoreVisitSummary>): SocialStats {
            val visited = summaries.filter(StoreVisitSummary::isVisited)

            return SocialStats(
                visitedStoreCount = visited.size,
                prefectureCount = visited.map(StoreVisitSummary::prefecture).distinct().size,
            )
        }
    }
}
```

- [ ] **Step 4: テストが通ることを確認する**

Run: `./gradlew :core:domain:test --tests "blue.starry.onemorecoffee.core.domain.model.SocialStatsTest"`
Expected: PASS

- [ ] **Step 5: 失敗するテストを書く（VisitPublicationPlan）**

`core/domain/src/test/java/blue/starry/onemorecoffee/core/domain/model/VisitPublicationPlanTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class VisitPublicationPlanTest {
    @Test
    fun of_emptyList_returnsNone() {
        assertThat(VisitPublicationPlan.of(emptyList())).isEqualTo(VisitPublicationPlan.None)
    }

    @Test
    fun of_atThreshold_returnsIndividual() {
        val visits = (1..5).map { FirstVisit(storeId = "$it", visitedOn = LocalDate.of(2026, 7, 1)) }

        val plan = VisitPublicationPlan.of(visits)

        assertThat(plan).isEqualTo(VisitPublicationPlan.Individual(visits))
    }

    @Test
    fun of_aboveThreshold_returnsBackfillWithCount() {
        val visits = (1..6).map { FirstVisit(storeId = "$it", visitedOn = LocalDate.of(2026, 7, 1)) }

        val plan = VisitPublicationPlan.of(visits)

        assertThat(plan).isEqualTo(VisitPublicationPlan.Backfill(count = 6))
    }
}
```

- [ ] **Step 6: テストが失敗することを確認する**

Run: `./gradlew :core:domain:test --tests "blue.starry.onemorecoffee.core.domain.model.VisitPublicationPlanTest"`
Expected: コンパイルエラーで FAIL

- [ ] **Step 7: FirstVisit と VisitPublicationPlan を実装する**

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/FirstVisit.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import java.time.LocalDate

// このインポート/記録で「はじめて訪問済みになった」店舗。ソーシャル公開の入力になる。
data class FirstVisit(
    val storeId: String,
    val visitedOn: LocalDate,
)
```

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/VisitPublicationPlan.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

// 一度の同期で初訪問が閾値を超えたら、個別イベントではなく件数だけの BACKFILL に畳む。
// フィードにインポート由来のイベントが数百件流れるのを防ぐ（設計書 §4.4）。
sealed interface VisitPublicationPlan {
    data object None : VisitPublicationPlan

    data class Individual(
        val visits: List<FirstVisit>,
    ) : VisitPublicationPlan

    data class Backfill(
        val count: Int,
    ) : VisitPublicationPlan

    companion object {
        const val BACKFILL_THRESHOLD = 5

        fun of(firstVisits: List<FirstVisit>): VisitPublicationPlan {
            return when {
                firstVisits.isEmpty() -> None
                firstVisits.size <= BACKFILL_THRESHOLD -> Individual(firstVisits)
                else -> Backfill(count = firstVisits.size)
            }
        }
    }
}
```

- [ ] **Step 8: テストが通ることを確認する**

Run: `./gradlew :core:domain:test`
Expected: PASS（既存テスト含む全件）

- [ ] **Step 9: 残りのモデルと SocialRepository を実装する（振る舞いなしのため TDD 対象外）**

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/SocialSession.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

data class SocialSession(
    val uid: String,
    val leagueId: String,
)
```

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/SocialProfile.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

data class SocialProfile(
    val displayName: String,
    val emoji: String,
)
```

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/League.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

data class League(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdBy: String,
)
```

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/LeagueMember.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import java.time.Instant

data class LeagueMember(
    val uid: String,
    val displayName: String,
    val emoji: String,
    val visitedStoreCount: Int,
    val prefectureCount: Int,
    val updatedAt: Instant?,
)
```

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/ActivityEvent.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import java.time.Instant
import java.time.LocalDate

sealed interface ActivityEvent {
    val id: String
    val uid: String
    val createdAt: Instant

    data class Visit(
        override val id: String,
        override val uid: String,
        override val createdAt: Instant,
        val storeId: String,
        val storeName: String,
        val prefecture: String,
        val visitedOn: LocalDate,
    ) : ActivityEvent

    data class Backfill(
        override val id: String,
        override val uid: String,
        override val createdAt: Instant,
        val count: Int,
    ) : ActivityEvent

    data class MemberJoined(
        override val id: String,
        override val uid: String,
        override val createdAt: Instant,
    ) : ActivityEvent
}
```

`core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/repository/SocialRepository.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.repository

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    // null = 未参加（またはソーシャル未構成）
    fun observeSession(): Flow<SocialSession?>

    fun observeLeague(): Flow<League?>

    fun observeMembers(): Flow<List<LeagueMember>>

    fun observeActivities(): Flow<List<ActivityEvent>>

    suspend fun createLeague(leagueName: String, profile: SocialProfile): League

    suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League

    suspend fun leaveLeague()

    // インポート等で新たに訪問済みになった店舗を公開する。失敗しても呼び出し元の処理は失敗させない。
    suspend fun publishFirstVisits(firstVisits: List<FirstVisit>)

    suspend fun refreshOwnStats()
}

// Firebase が未構成（secrets 未設定）の端末で参加系操作をしたときに投げる
class SocialUnavailableException : IllegalStateException("Firebase が構成されていません")
```

- [ ] **Step 10: ビルドとテストの確認、Commit**

Run: `./gradlew :core:domain:test`
Expected: PASS

```bash
git add core/domain/src
git commit -m "feat: ソーシャル機能のドメインモデルと SocialRepository を追加"
```

---

### Task 4: 招待コード生成と Firestore ドキュメントマッパー

**Files:**
- Create: `core/social/src/main/java/blue/starry/onemorecoffee/core/social/InviteCode.kt`
- Create: `core/social/src/main/java/blue/starry/onemorecoffee/core/social/SocialDocuments.kt`
- Test: `core/social/src/test/java/blue/starry/onemorecoffee/core/social/InviteCodeTest.kt`
- Test: `core/social/src/test/java/blue/starry/onemorecoffee/core/social/SocialDocumentsTest.kt`

**Interfaces:**
- Consumes: Task 3 の `ActivityEvent` / `LeagueMember` / `SocialStats` / `SocialProfile` / `FirstVisit`、`com.google.firebase.Timestamp`（純粋なクラスなので unit test 可能）
- Produces:
  - `InviteCode.generate(): String`（8 文字、紛らわしい文字なし）
  - `SocialDocuments.toActivityEvent(id: String, data: Map<String, Any?>): ActivityEvent?`
  - `SocialDocuments.toLeagueMember(uid: String, data: Map<String, Any?>): LeagueMember`
  - `SocialDocuments.visitDocument(uid: String, storeId: String, storeName: String, prefecture: String, visitedOn: LocalDate): Map<String, Any>`
  - `SocialDocuments.backfillDocument(uid: String, count: Int): Map<String, Any>`
  - `SocialDocuments.memberJoinedDocument(uid: String): Map<String, Any>`
  - `SocialDocuments.memberDocument(profile: SocialProfile, stats: SocialStats): Map<String, Any>`
  - `SocialDocuments.statsUpdate(stats: SocialStats): Map<String, Any>`
  - `SocialDocuments.visitActivityId(uid: String, storeId: String): String` / `backfillActivityId(uid: String, count: Int): String` / `memberJoinedActivityId(uid: String): String`

- [ ] **Step 1: 失敗するテストを書く（InviteCode）**

`core/social/src/test/java/blue/starry/onemorecoffee/core/social/InviteCodeTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InviteCodeTest {
    @Test
    fun generate_returnsEightCharactersFromUnambiguousAlphabet() {
        repeat(100) {
            val code = InviteCode.generate()

            assertThat(code).hasLength(8)
            assertThat(code.all { it in InviteCode.ALPHABET }).isTrue()
        }
    }

    @Test
    fun alphabet_excludesAmbiguousCharacters() {
        for (ambiguous in listOf('0', 'O', '1', 'I', 'L')) {
            assertThat(InviteCode.ALPHABET).doesNotContain(ambiguous.toString())
        }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./gradlew :core:social:test --tests "blue.starry.onemorecoffee.core.social.InviteCodeTest"`
Expected: コンパイルエラーで FAIL

- [ ] **Step 3: InviteCode を実装する**

`core/social/src/main/java/blue/starry/onemorecoffee/core/social/InviteCode.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import kotlin.random.Random

// 読み間違えやすい 0/O/1/I/L を除いた 8 文字コード。
// 数人規模なので衝突確率（31^8 ≒ 8500 億分の N）は無視する。
object InviteCode {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(): String {
        return buildString {
            repeat(8) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
    }
}
```

- [ ] **Step 4: テストが通ることを確認する**

Run: `./gradlew :core:social:test --tests "blue.starry.onemorecoffee.core.social.InviteCodeTest"`
Expected: PASS

- [ ] **Step 5: 失敗するテストを書く（SocialDocuments）**

`core/social/src/test/java/blue/starry/onemorecoffee/core/social/SocialDocumentsTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialStats
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class SocialDocumentsTest {
    private val createdAt = Timestamp(1_751_900_000, 0)

    @Test
    fun toActivityEvent_parsesVisit() {
        val data = mapOf(
            "uid" to "user1",
            "type" to "VISIT",
            "storeId" to "1783",
            "storeName" to "丸の内オアゾ店",
            "prefecture" to "東京都",
            "visitedOn" to "2026-07-05",
            "createdAt" to createdAt,
        )

        val event = SocialDocuments.toActivityEvent(id = "user1_1783", data = data)

        assertThat(event).isEqualTo(
            ActivityEvent.Visit(
                id = "user1_1783",
                uid = "user1",
                createdAt = Instant.ofEpochSecond(1_751_900_000),
                storeId = "1783",
                storeName = "丸の内オアゾ店",
                prefecture = "東京都",
                visitedOn = LocalDate.of(2026, 7, 5),
            ),
        )
    }

    @Test
    fun toActivityEvent_parsesBackfillAndMemberJoined() {
        val backfill = SocialDocuments.toActivityEvent(
            id = "user1_backfill_287",
            data = mapOf("uid" to "user1", "type" to "BACKFILL", "count" to 287L, "createdAt" to createdAt),
        )
        val joined = SocialDocuments.toActivityEvent(
            id = "user1_joined",
            data = mapOf("uid" to "user1", "type" to "MEMBER_JOINED", "createdAt" to createdAt),
        )

        assertThat(backfill).isEqualTo(
            ActivityEvent.Backfill(
                id = "user1_backfill_287",
                uid = "user1",
                createdAt = Instant.ofEpochSecond(1_751_900_000),
                count = 287,
            ),
        )
        assertThat(joined).isEqualTo(
            ActivityEvent.MemberJoined(
                id = "user1_joined",
                uid = "user1",
                createdAt = Instant.ofEpochSecond(1_751_900_000),
            ),
        )
    }

    @Test
    fun toActivityEvent_unknownType_returnsNull() {
        val event = SocialDocuments.toActivityEvent(
            id = "x",
            data = mapOf("uid" to "user1", "type" to "REACTION", "createdAt" to createdAt),
        )

        assertThat(event).isNull()
    }

    @Test
    fun visitDocument_roundTripsThroughToActivityEvent() {
        val document = SocialDocuments.visitDocument(
            uid = "user1",
            storeId = "1783",
            storeName = "丸の内オアゾ店",
            prefecture = "東京都",
            visitedOn = LocalDate.of(2026, 7, 5),
        )
        // createdAt は FieldValue.serverTimestamp() なのでサーバー付与値に置き換えて往復を確認する
        val event = SocialDocuments.toActivityEvent(
            id = SocialDocuments.visitActivityId(uid = "user1", storeId = "1783"),
            data = document + mapOf("createdAt" to createdAt),
        )

        assertThat(event).isInstanceOf(ActivityEvent.Visit::class.java)
        assertThat((event as ActivityEvent.Visit).storeName).isEqualTo("丸の内オアゾ店")
    }

    @Test
    fun toLeagueMember_parsesStatsWithDefaults() {
        val member = SocialDocuments.toLeagueMember(
            uid = "user1",
            data = mapOf(
                "displayName" to "ねぴ",
                "emoji" to "☕",
                "stats" to mapOf(
                    "visitedStoreCount" to 42L,
                    "prefectureCount" to 3L,
                    "updatedAt" to createdAt,
                ),
            ),
        )

        assertThat(member.displayName).isEqualTo("ねぴ")
        assertThat(member.visitedStoreCount).isEqualTo(42)
        assertThat(member.prefectureCount).isEqualTo(3)
        assertThat(member.updatedAt).isEqualTo(Instant.ofEpochSecond(1_751_900_000))
    }

    @Test
    fun memberDocument_containsProfileAndStats() {
        val document = SocialDocuments.memberDocument(
            profile = SocialProfile(displayName = "ねぴ", emoji = "☕"),
            stats = SocialStats(visitedStoreCount = 42, prefectureCount = 3),
        )

        val member = SocialDocuments.toLeagueMember(uid = "user1", data = document)

        assertThat(member.displayName).isEqualTo("ねぴ")
        assertThat(member.emoji).isEqualTo("☕")
        assertThat(member.visitedStoreCount).isEqualTo(42)
    }
}
```

- [ ] **Step 6: テストが失敗することを確認する**

Run: `./gradlew :core:social:test --tests "blue.starry.onemorecoffee.core.social.SocialDocumentsTest"`
Expected: コンパイルエラーで FAIL

- [ ] **Step 7: SocialDocuments を実装する**

`core/social/src/main/java/blue/starry/onemorecoffee/core/social/SocialDocuments.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialStats
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.time.Instant
import java.time.LocalDate

// Firestore ドキュメント (Map) とドメインモデルの相互変換。
// Firestore SDK の toObject() は使わず明示的に詰め替える（unit test しやすく、スキーマ変更に気付きやすい）。
object SocialDocuments {
    private const val TYPE_VISIT = "VISIT"
    private const val TYPE_BACKFILL = "BACKFILL"
    private const val TYPE_MEMBER_JOINED = "MEMBER_JOINED"

    fun visitActivityId(uid: String, storeId: String): String = "${uid}_$storeId"

    fun backfillActivityId(uid: String, count: Int): String = "${uid}_backfill_$count"

    fun memberJoinedActivityId(uid: String): String = "${uid}_joined"

    fun visitDocument(
        uid: String,
        storeId: String,
        storeName: String,
        prefecture: String,
        visitedOn: LocalDate,
    ): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "type" to TYPE_VISIT,
            "storeId" to storeId,
            "storeName" to storeName,
            "prefecture" to prefecture,
            "visitedOn" to visitedOn.toString(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    fun backfillDocument(uid: String, count: Int): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "type" to TYPE_BACKFILL,
            "count" to count.toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    fun memberJoinedDocument(uid: String): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "type" to TYPE_MEMBER_JOINED,
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    fun memberDocument(profile: SocialProfile, stats: SocialStats): Map<String, Any> {
        return mapOf(
            "displayName" to profile.displayName,
            "emoji" to profile.emoji,
            "joinedAt" to FieldValue.serverTimestamp(),
            "stats" to statsMap(stats),
        )
    }

    fun statsUpdate(stats: SocialStats): Map<String, Any> {
        return mapOf("stats" to statsMap(stats))
    }

    private fun statsMap(stats: SocialStats): Map<String, Any> {
        return mapOf(
            "visitedStoreCount" to stats.visitedStoreCount.toLong(),
            "prefectureCount" to stats.prefectureCount.toLong(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }

    fun toActivityEvent(id: String, data: Map<String, Any?>): ActivityEvent? {
        val uid = data["uid"] as? String ?: return null
        val createdAt = (data["createdAt"] as? Timestamp)?.toInstant() ?: Instant.EPOCH

        return when (data["type"]) {
            TYPE_VISIT -> ActivityEvent.Visit(
                id = id,
                uid = uid,
                createdAt = createdAt,
                storeId = data["storeId"] as? String ?: return null,
                storeName = data["storeName"] as? String ?: return null,
                prefecture = data["prefecture"] as? String ?: "",
                visitedOn = (data["visitedOn"] as? String)?.let(LocalDate::parse) ?: return null,
            )
            TYPE_BACKFILL -> ActivityEvent.Backfill(
                id = id,
                uid = uid,
                createdAt = createdAt,
                count = (data["count"] as? Long)?.toInt() ?: 0,
            )
            TYPE_MEMBER_JOINED -> ActivityEvent.MemberJoined(
                id = id,
                uid = uid,
                createdAt = createdAt,
            )
            // 未知の type は将来のイベント種別（前方互換のため無視）
            else -> null
        }
    }

    fun toLeagueMember(uid: String, data: Map<String, Any?>): LeagueMember {
        @Suppress("UNCHECKED_CAST")
        val stats = data["stats"] as? Map<String, Any?> ?: emptyMap()

        return LeagueMember(
            uid = uid,
            displayName = data["displayName"] as? String ?: "（名前未設定）",
            emoji = data["emoji"] as? String ?: "☕",
            visitedStoreCount = (stats["visitedStoreCount"] as? Long)?.toInt() ?: 0,
            prefectureCount = (stats["prefectureCount"] as? Long)?.toInt() ?: 0,
            updatedAt = (stats["updatedAt"] as? Timestamp)?.toInstant(),
        )
    }

    private fun Timestamp.toInstant(): Instant = toDate().toInstant()
}
```

- [ ] **Step 8: テストが通ることを確認する**

Run: `./gradlew :core:social:test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add core/social/src
git commit -m "feat: 招待コード生成と Firestore ドキュメントマッパーを追加"
```

---

### Task 5: セッション永続化と FirestoreSocialRepository

**Files:**
- Create: `core/social/src/main/java/blue/starry/onemorecoffee/core/social/SocialSessionStore.kt`
- Create: `core/social/src/main/java/blue/starry/onemorecoffee/core/social/FirestoreSocialRepository.kt`
- Create: `core/social/src/main/java/blue/starry/onemorecoffee/core/social/di/SocialModule.kt`
- Test: `core/social/src/test/java/blue/starry/onemorecoffee/core/social/SocialSessionStoreTest.kt`

**Interfaces:**
- Consumes: Task 3 の `SocialRepository` インターフェースと各モデル、Task 4 の `SocialDocuments` / `InviteCode`、Task 2 の `FirebaseInitializer.isAvailable`、既存の `StoreRepository.observeStoreSummaries()`
- Produces: Hilt バインディング `SocialRepository` → `FirestoreSocialRepository`（Task 6 の `VisitRepositoryImpl` と Task 7 の ViewModel が注入で受け取る）、`SocialSessionStore.leagueId: Flow<String?>` / `save(leagueId)` / `clear()`

- [ ] **Step 1: 失敗するテストを書く（SocialSessionStore、Robolectric）**

`core/social/src/test/java/blue/starry/onemorecoffee/core/social/SocialSessionStoreTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun saveAndObserve_roundTrips() = runTest {
        val store = SocialSessionStore(context)

        assertThat(store.leagueId.first()).isNull()

        store.save(leagueId = "league1")

        assertThat(store.leagueId.first()).isEqualTo("league1")

        store.clear()

        assertThat(store.leagueId.first()).isNull()
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./gradlew :core:social:test --tests "blue.starry.onemorecoffee.core.social.SocialSessionStoreTest"`
Expected: コンパイルエラーで FAIL

- [ ] **Step 3: SocialSessionStore を実装する**

`core/social/src/main/java/blue/starry/onemorecoffee/core/social/SocialSessionStore.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.socialDataStore by preferencesDataStore(name = "social_session")

// 参加中リーグ ID の永続化。uid は Firebase Auth が保持するためここでは持たない。
@Singleton
class SocialSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val leagueIdKey = stringPreferencesKey("league_id")

    val leagueId: Flow<String?> = context.socialDataStore.data.map { preferences ->
        preferences[leagueIdKey]
    }

    suspend fun save(leagueId: String) {
        context.socialDataStore.edit { preferences ->
            preferences[leagueIdKey] = leagueId
        }
    }

    suspend fun clear() {
        context.socialDataStore.edit { preferences ->
            preferences.remove(leagueIdKey)
        }
    }
}
```

- [ ] **Step 4: テストが通ることを確認する**

Run: `./gradlew :core:social:test --tests "blue.starry.onemorecoffee.core.social.SocialSessionStoreTest"`
Expected: PASS

- [ ] **Step 5: FirestoreSocialRepository を実装する**

Firestore SDK に直接依存する層。ロジックはすべて Task 3/4 のテスト済み部品に寄せてあり、この層は「呼び出しの並び」だけを持つ（このため unit test は書かず、Task 12 の手動 E2E で確認する）。

`core/social/src/main/java/blue/starry/onemorecoffee/core/social/FirestoreSocialRepository.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social

import android.content.Context
import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import blue.starry.onemorecoffee.core.domain.model.SocialStats
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.model.VisitPublicationPlan
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import blue.starry.onemorecoffee.core.domain.repository.SocialUnavailableException
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSocialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SocialSessionStore,
    private val storeRepository: StoreRepository,
) : SocialRepository {
    override fun observeSession(): Flow<SocialSession?> {
        return sessionStore.leagueId.map { leagueId ->
            // uid は匿名サインイン時に確定し、その後は変化しない前提（リーグ参加前に必ずサインインする）
            val uid = if (isAvailable()) FirebaseAuth.getInstance().currentUser?.uid else null

            if (leagueId != null && uid != null) SocialSession(uid = uid, leagueId = leagueId) else null
        }
    }

    override fun observeLeague(): Flow<League?> {
        return observeInLeague(noLeague = null) { leagueId ->
            callbackFlow {
                val registration = firestore().collection("leagues").document(leagueId)
                    .addSnapshotListener { snapshot, _ ->
                        trySend(snapshot?.takeIf(DocumentSnapshot::exists)?.let(::toLeague))
                    }
                awaitClose { registration.remove() }
            }
        }
    }

    override fun observeMembers(): Flow<List<LeagueMember>> {
        return observeInLeague(noLeague = emptyList<LeagueMember>()) { leagueId ->
            callbackFlow {
                val registration = firestore().collection("leagues").document(leagueId).collection("members")
                    .addSnapshotListener { snapshot, _ ->
                        val members = snapshot?.documents.orEmpty().map { document ->
                            SocialDocuments.toLeagueMember(uid = document.id, data = document.estimatedData())
                        }
                        trySend(members)
                    }
                awaitClose { registration.remove() }
            }
        }.map { members -> members.orEmpty() }
    }

    override fun observeActivities(): Flow<List<ActivityEvent>> {
        return observeInLeague(noLeague = emptyList<ActivityEvent>()) { leagueId ->
            callbackFlow {
                val registration = firestore().collection("leagues").document(leagueId).collection("activities")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener { snapshot, _ ->
                        val events = snapshot?.documents.orEmpty().mapNotNull { document ->
                            SocialDocuments.toActivityEvent(id = document.id, data = document.estimatedData())
                        }
                        trySend(events)
                    }
                awaitClose { registration.remove() }
            }
        }.map { events -> events.orEmpty() }
    }

    override suspend fun createLeague(leagueName: String, profile: SocialProfile): League {
        val uid = signInAnonymously()
        val firestore = firestore()
        val leagueRef = firestore.collection("leagues").document()
        val inviteCode = InviteCode.generate()
        val stats = currentStats()

        // セキュリティルールの isMember は既存の member doc を参照するため、
        // member doc を作るバッチと activities を作るバッチの 2 段階に分ける
        firestore.batch().apply {
            set(firestore.collection("inviteCodes").document(inviteCode), mapOf("leagueId" to leagueRef.id))
            set(
                leagueRef,
                mapOf(
                    "name" to leagueName,
                    "inviteCode" to inviteCode,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            set(leagueRef.collection("members").document(uid), SocialDocuments.memberDocument(profile, stats))
        }.commit().await()

        publishJoinActivities(leagueId = leagueRef.id, uid = uid, stats = stats)
        sessionStore.save(leagueId = leagueRef.id)

        return League(id = leagueRef.id, name = leagueName, inviteCode = inviteCode, createdBy = uid)
    }

    override suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League {
        val uid = signInAnonymously()
        val firestore = firestore()
        val normalizedCode = inviteCode.trim().uppercase()
        val codeSnapshot = firestore.collection("inviteCodes").document(normalizedCode).get().await()
        val leagueId = codeSnapshot.getString("leagueId")
            ?: throw IllegalArgumentException("招待コードが見つかりません")
        val stats = currentStats()

        firestore.collection("leagues").document(leagueId).collection("members").document(uid)
            .set(SocialDocuments.memberDocument(profile, stats))
            .await()

        publishJoinActivities(leagueId = leagueId, uid = uid, stats = stats)
        sessionStore.save(leagueId = leagueId)

        val leagueSnapshot = firestore.collection("leagues").document(leagueId).get().await()

        return toLeague(leagueSnapshot)
    }

    override suspend fun leaveLeague() {
        val session = observeSession().first() ?: return
        val firestore = firestore()
        val leagueRef = firestore.collection("leagues").document(session.leagueId)
        val ownActivities = leagueRef.collection("activities")
            .whereEqualTo("uid", session.uid)
            .get()
            .await()

        firestore.batch().apply {
            ownActivities.documents.forEach { document -> delete(document.reference) }
            delete(leagueRef.collection("members").document(session.uid))
        }.commit().await()

        sessionStore.clear()
    }

    override suspend fun publishFirstVisits(firstVisits: List<FirstVisit>) {
        val session = observeSession().first() ?: return
        val summaries = storeRepository.observeStoreSummaries().first()
        val summariesById = summaries.associateBy(StoreVisitSummary::id)
        val stats = SocialStats.from(summaries)
        val firestore = firestore()
        val activities = firestore.collection("leagues").document(session.leagueId).collection("activities")
        val members = firestore.collection("leagues").document(session.leagueId).collection("members")

        val batch = firestore.batch()

        when (val plan = VisitPublicationPlan.of(firstVisits)) {
            VisitPublicationPlan.None -> Unit
            is VisitPublicationPlan.Individual -> {
                plan.visits.forEach { visit ->
                    val store = summariesById[visit.storeId] ?: return@forEach

                    batch.set(
                        activities.document(SocialDocuments.visitActivityId(uid = session.uid, storeId = visit.storeId)),
                        SocialDocuments.visitDocument(
                            uid = session.uid,
                            storeId = visit.storeId,
                            storeName = store.name,
                            prefecture = store.prefecture,
                            visitedOn = visit.visitedOn,
                        ),
                    )
                }
            }
            is VisitPublicationPlan.Backfill -> {
                batch.set(
                    activities.document(SocialDocuments.backfillActivityId(uid = session.uid, count = plan.count)),
                    SocialDocuments.backfillDocument(uid = session.uid, count = plan.count),
                )
            }
        }

        batch.set(members.document(session.uid), SocialDocuments.statsUpdate(stats), SetOptions.merge())
        // await しない: オフラインでもローカルキューに積まれ、再接続時に自動送信される
        batch.commit()
    }

    override suspend fun refreshOwnStats() {
        val session = observeSession().first() ?: return
        val stats = currentStats()

        firestore().collection("leagues").document(session.leagueId)
            .collection("members").document(session.uid)
            .set(SocialDocuments.statsUpdate(stats), SetOptions.merge())
            .await()
    }

    private fun publishJoinActivities(leagueId: String, uid: String, stats: SocialStats) {
        val activities = firestore().collection("leagues").document(leagueId).collection("activities")
        val batch = firestore().batch()

        batch.set(
            activities.document(SocialDocuments.memberJoinedActivityId(uid)),
            SocialDocuments.memberJoinedDocument(uid),
        )

        if (stats.visitedStoreCount > 0) {
            batch.set(
                activities.document(SocialDocuments.backfillActivityId(uid = uid, count = stats.visitedStoreCount)),
                SocialDocuments.backfillDocument(uid = uid, count = stats.visitedStoreCount),
            )
        }

        batch.commit()
    }

    private suspend fun currentStats(): SocialStats {
        return SocialStats.from(storeRepository.observeStoreSummaries().first())
    }

    private suspend fun signInAnonymously(): String {
        if (!isAvailable()) throw SocialUnavailableException()

        val auth = FirebaseAuth.getInstance()

        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    // 未参加（または Firebase 未構成）の間は noLeague を流し、参加中はリーグ配下のリスナーに切り替える
    private fun <T> observeInLeague(noLeague: T?, block: (leagueId: String) -> Flow<T?>): Flow<T?> {
        return sessionStore.leagueId.flatMapLatest { leagueId ->
            if (leagueId == null || !isAvailable()) flowOf(noLeague) else block(leagueId)
        }
    }

    private fun toLeague(snapshot: DocumentSnapshot): League {
        return League(
            id = snapshot.id,
            name = snapshot.getString("name") ?: "",
            inviteCode = snapshot.getString("inviteCode") ?: "",
            createdBy = snapshot.getString("createdBy") ?: "",
        )
    }

    // serverTimestamp が未確定のローカルエコーでは推定値を使い、createdAt が null にならないようにする
    private fun DocumentSnapshot.estimatedData(): Map<String, Any?> {
        return getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()
    }

    private fun firestore(): FirebaseFirestore {
        if (!isAvailable()) throw SocialUnavailableException()

        return FirebaseFirestore.getInstance()
    }

    private fun isAvailable(): Boolean = FirebaseInitializer.isAvailable(context)
}
```

- [ ] **Step 6: Hilt モジュールを作成する**

`core/social/src/main/java/blue/starry/onemorecoffee/core/social/di/SocialModule.kt`:

```kotlin
package blue.starry.onemorecoffee.core.social.di

import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import blue.starry.onemorecoffee.core.social.FirestoreSocialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {
    @Binds
    abstract fun bindSocialRepository(impl: FirestoreSocialRepository): SocialRepository
}
```

- [ ] **Step 7: ビルドとテストの確認、Commit**

Run: `./gradlew :core:social:test assembleDebug`
Expected: PASS / `BUILD SUCCESSFUL`

```bash
git add core/social/src
git commit -m "feat: Firestore ベースの SocialRepository 実装を追加"
```

---

### Task 6: インポートフローへの publish フック（core/data）

**Files:**
- Modify: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/dao/VisitDao.kt`
- Modify: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/repository/VisitRepositoryImpl.kt`
- Test: `core/data/src/test/java/blue/starry/onemorecoffee/core/data/database/VisitDaoTest.kt`（追記）
- Test: `core/data/src/test/java/blue/starry/onemorecoffee/core/data/repository/VisitRepositoryImplTest.kt`（修正・追記）

**Interfaces:**
- Consumes: Task 3 の `SocialRepository.publishFirstVisits(List<FirstVisit>)` / `FirstVisit`
- Produces: `VisitDao.visitedStoreIds(): List<String>`、インポート時に初訪問店舗のみが publish される振る舞い

注意: 既存の `VisitRepositoryImplTest` は `VisitRepositoryImpl(visitDao = ..., storeDao = ...)` の 2 引数コンストラクタを前提にしている。コンストラクタ変更に合わせて既存テストの生成箇所を `socialRepository = FakeSocialRepository()` 付きに修正すること（既存アサーションは変更しない）。

- [ ] **Step 1: 失敗するテストを書く（VisitDao.visitedStoreIds）**

`core/data/src/test/java/blue/starry/onemorecoffee/core/data/database/VisitDaoTest.kt` の既存テストクラス内にメソッドを追記（既存の `database` フィールドと `visit()` ヘルパーをそのまま使う）:

```kotlin
    @Test
    fun visitedStoreIds_returnsDistinctStoreIds() = runTest {
        database.visitDao().insertIgnore(
            listOf(
                visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 7, 1)),
                visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 7, 2)),
                visit(storeId = "store-2", visitedOn = LocalDate.of(2026, 7, 1)),
            ),
        )

        assertThat(database.visitDao().visitedStoreIds()).containsExactly("store-1", "store-2")
    }
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./gradlew :core:data:test --tests "blue.starry.onemorecoffee.core.data.database.VisitDaoTest"`
Expected: コンパイルエラー（`visitedStoreIds` 未定義）で FAIL

- [ ] **Step 3: VisitDao にクエリを追加する**

`VisitDao.kt` の `count()` の後に追加:

```kotlin
    @Query("SELECT DISTINCT storeId FROM visits")
    suspend fun visitedStoreIds(): List<String>
```

- [ ] **Step 4: テストが通ることを確認する**

Run: `./gradlew :core:data:test --tests "blue.starry.onemorecoffee.core.data.database.VisitDaoTest"`
Expected: PASS

- [ ] **Step 5: 失敗するテストを書く（VisitRepositoryImpl の publish 連携）**

`VisitRepositoryImplTest.kt` に import を追加:

```kotlin
import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
```

テストクラス内に FakeSocialRepository を private class として追加:

```kotlin
    private class FakeSocialRepository : SocialRepository {
        val publishedFirstVisits = mutableListOf<List<FirstVisit>>()
        var shouldFail = false

        override suspend fun publishFirstVisits(firstVisits: List<FirstVisit>) {
            if (shouldFail) throw IllegalStateException("publish failed")
            publishedFirstVisits.add(firstVisits)
        }

        override fun observeSession(): Flow<SocialSession?> = flowOf(null)
        override fun observeLeague(): Flow<League?> = flowOf(null)
        override fun observeMembers(): Flow<List<LeagueMember>> = flowOf(emptyList())
        override fun observeActivities(): Flow<List<ActivityEvent>> = flowOf(emptyList())
        override suspend fun createLeague(leagueName: String, profile: SocialProfile): League = error("unused")
        override suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League = error("unused")
        override suspend fun leaveLeague() = Unit
        override suspend fun refreshOwnStats() = Unit
    }
```

テストを追加（JSON は既存テストと同じ `store_id` / `first_visit_date` / `last_visit_date` 形式。first と last が同一日時の場合、パーサーは 1 件の Visit を生成する）:

```kotlin
    @Test
    fun importStarbucksVisits_publishesOnlyFirstVisitsOfKnownStores() = runTest {
        database.storeDao().upsertAll(listOf(store("known-store"), store("new-store")))
        database.visitDao().insertIgnore(
            visit(storeId = "known-store", visitedOn = LocalDate.of(2026, 5, 1)),
        )
        val socialRepository = FakeSocialRepository()
        val repository = VisitRepositoryImpl(
            visitDao = database.visitDao(),
            storeDao = database.storeDao(),
            socialRepository = socialRepository,
        )

        repository.importStarbucksVisits(
            """
            [
              {
                "store_id": "known-store",
                "first_visit_date": "2026-05-01T10:00:00+09:00",
                "last_visit_date": "2026-07-01T10:00:00+09:00"
              },
              {
                "store_id": "new-store",
                "first_visit_date": "2026-07-02T10:00:00+09:00",
                "last_visit_date": "2026-07-05T10:00:00+09:00"
              },
              {
                "store_id": "unknown-store",
                "first_visit_date": "2026-07-03T10:00:00+09:00",
                "last_visit_date": "2026-07-03T10:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        // 公開対象は「マスタに存在し、今回はじめて訪問済みになった」new-store のみ。
        // known-store は再訪、unknown-store はマスタ未知のため対象外
        assertThat(socialRepository.publishedFirstVisits).hasSize(1)
        val published = socialRepository.publishedFirstVisits.single()
        assertThat(published.map(FirstVisit::storeId)).containsExactly("new-store")
        // 同一店舗で複数の訪問が挿入された場合は最古の訪問日を採用する
        assertThat(published.single().visitedOn).isEqualTo(LocalDate.of(2026, 7, 2))
    }

    @Test
    fun importStarbucksVisits_publishFailure_doesNotAffectImportResult() = runTest {
        database.storeDao().upsertAll(listOf(store("new-store")))
        val socialRepository = FakeSocialRepository().apply { shouldFail = true }
        val repository = VisitRepositoryImpl(
            visitDao = database.visitDao(),
            storeDao = database.storeDao(),
            socialRepository = socialRepository,
        )

        val result = repository.importStarbucksVisits(
            """
            [
              {
                "store_id": "new-store",
                "first_visit_date": "2026-07-02T10:00:00+09:00",
                "last_visit_date": "2026-07-02T10:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)
    }
```

- [ ] **Step 6: テストが失敗することを確認する**

Run: `./gradlew :core:data:test --tests "blue.starry.onemorecoffee.core.data.repository.VisitRepositoryImplTest"`
Expected: コンパイルエラー（コンストラクタ引数不一致）で FAIL

- [ ] **Step 7: VisitRepositoryImpl を拡張する**

`VisitRepositoryImpl.kt` を次に置き換え:

```kotlin
package blue.starry.onemorecoffee.core.data.repository

import android.util.Log
import android.webkit.CookieManager
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.data.importer.StarbucksVisitImportParser
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class VisitRepositoryImpl @Inject constructor(
    private val visitDao: VisitDao,
    private val storeDao: StoreDao,
    private val socialRepository: SocialRepository,
) : VisitRepository {
    override suspend fun importStarbucksVisits(json: String): VisitImportResult {
        val parsed = StarbucksVisitImportParser.parse(json)
        val knownStoreIds = storeDao.ids().toSet()
        val previouslyVisitedStoreIds = visitDao.visitedStoreIds().toSet()
        val insertResults = visitDao.insertIgnore(parsed.visits)
        val inserted = insertResults.count { id -> id != -1L }
        val unknownStoreVisits = parsed.visits
            .map { visit -> visit.storeId }
            .filterNot { storeId -> storeId in knownStoreIds }
            .distinct()
            .size

        publishFirstVisits(
            insertedVisits = parsed.visits.zip(insertResults)
                .filter { (_, rowId) -> rowId != -1L }
                .map { (visit, _) -> visit },
            knownStoreIds = knownStoreIds,
            previouslyVisitedStoreIds = previouslyVisitedStoreIds,
        )

        return VisitImportResult(
            inserted = inserted,
            duplicated = parsed.visits.size - inserted,
            unknownStoreVisits = unknownStoreVisits,
            failed = parsed.failed,
        )
    }

    private suspend fun publishFirstVisits(
        insertedVisits: List<VisitEntity>,
        knownStoreIds: Set<String>,
        previouslyVisitedStoreIds: Set<String>,
    ) {
        val firstVisits = insertedVisits
            // マスタ未知の店舗（閉店等）は名前解決できないため公開対象外
            .filter { visit -> visit.storeId in knownStoreIds && visit.storeId !in previouslyVisitedStoreIds }
            .groupBy { visit -> visit.storeId }
            .map { (storeId, visits) ->
                FirstVisit(storeId = storeId, visitedOn = visits.minOf { visit -> visit.visitedOn })
            }

        // ソーシャル公開の失敗はインポート自体の成否に影響させない（コルーチンのキャンセルだけは伝播させる）
        try {
            socialRepository.publishFirstVisits(firstVisits)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VisitRepository", "Failed to publish first visits to social league", e)
        }
    }
}
```

- [ ] **Step 8: 既存テストの生成箇所を修正し、全テストが通ることを確認する**

既存テスト `importStarbucksVisits_insertsVisitsAndReportsDuplicatesUnknownDistinctStoresAndParseFailures` 内の生成を次に変更（アサーションは変更しない）:

```kotlin
        val repository = VisitRepositoryImpl(
            visitDao = database.visitDao(),
            storeDao = database.storeDao(),
            socialRepository = FakeSocialRepository(),
        )
```

Run: `./gradlew :core:data:test`
Expected: PASS（既存 + 新規全件）

- [ ] **Step 9: Commit**

```bash
git add core/data/src
git commit -m "feat: インポート時に初訪問店舗をソーシャル公開するフックを追加"
```

---

### Task 7: フィード表示用の整形ロジック（feature/friends）

**Files:**
- Create: `feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FeedFormatters.kt`
- Test: `feature/friends/src/test/java/blue/starry/onemorecoffee/feature/friends/FeedFormattersTest.kt`

**Interfaces:**
- Consumes: Task 3 の `ActivityEvent`
- Produces（Task 9 の UI が使用）:
  - `formatRelativeTime(createdAt: Instant, now: Instant): String`
  - `feedItemText(event: ActivityEvent, memberName: String): String`

- [ ] **Step 1: 失敗するテストを書く**

`feature/friends/src/test/java/blue/starry/onemorecoffee/feature/friends/FeedFormattersTest.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class FeedFormattersTest {
    private val now = Instant.parse("2026-07-07T12:00:00Z")

    @Test
    fun formatRelativeTime_coversAllRanges() {
        assertThat(formatRelativeTime(now.minus(Duration.ofSeconds(30)), now)).isEqualTo("たった今")
        assertThat(formatRelativeTime(now.minus(Duration.ofMinutes(5)), now)).isEqualTo("5 分前")
        assertThat(formatRelativeTime(now.minus(Duration.ofHours(3)), now)).isEqualTo("3 時間前")
        assertThat(formatRelativeTime(now.minus(Duration.ofDays(2)), now)).isEqualTo("2 日前")
        assertThat(formatRelativeTime(now.minus(Duration.ofDays(10)), now)).isEqualTo("6/27")
    }

    @Test
    fun feedItemText_visit() {
        val event = ActivityEvent.Visit(
            id = "u1_1783",
            uid = "u1",
            createdAt = now,
            storeId = "1783",
            storeName = "丸の内オアゾ店",
            prefecture = "東京都",
            visitedOn = LocalDate.of(2026, 7, 7),
        )

        assertThat(feedItemText(event, memberName = "ねぴ"))
            .isEqualTo("ねぴ さんが 丸の内オアゾ店（東京都）を初訪問")
    }

    @Test
    fun feedItemText_backfillAndJoined() {
        val backfill = ActivityEvent.Backfill(id = "b", uid = "u1", createdAt = now, count = 287)
        val joined = ActivityEvent.MemberJoined(id = "j", uid = "u1", createdAt = now)

        assertThat(feedItemText(backfill, memberName = "ねぴ")).isEqualTo("ねぴ さんが過去の訪問 287 店舗分を登録")
        assertThat(feedItemText(joined, memberName = "ねぴ")).isEqualTo("ねぴ さんがリーグに参加")
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./gradlew :feature:friends:test`
Expected: コンパイルエラーで FAIL

- [ ] **Step 3: FeedFormatters を実装する**

`feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FeedFormatters.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// 利用者は全員日本在住のため Asia/Tokyo 固定（設計書 §4.2）
private val zone = ZoneId.of("Asia/Tokyo")

fun formatRelativeTime(createdAt: Instant, now: Instant): String {
    val duration = Duration.between(createdAt, now)

    return when {
        duration.toMinutes() < 1 -> "たった今"
        duration.toHours() < 1 -> "${duration.toMinutes()} 分前"
        duration.toDays() < 1 -> "${duration.toHours()} 時間前"
        duration.toDays() < 7 -> "${duration.toDays()} 日前"
        else -> {
            val date = createdAt.atZone(zone).toLocalDate()
            "${date.monthValue}/${date.dayOfMonth}"
        }
    }
}

fun feedItemText(event: ActivityEvent, memberName: String): String {
    return when (event) {
        is ActivityEvent.Visit -> "$memberName さんが ${event.storeName}（${event.prefecture}）を初訪問"
        is ActivityEvent.Backfill -> "$memberName さんが過去の訪問 ${event.count} 店舗分を登録"
        is ActivityEvent.MemberJoined -> "$memberName さんがリーグに参加"
    }
}
```

- [ ] **Step 4: テストが通ることを確認して Commit**

Run: `./gradlew :feature:friends:test`
Expected: PASS

```bash
git add feature/friends/src
git commit -m "feat: フィード表示用の相対時刻と文言整形を追加"
```

---

### Task 8: FriendsScreenViewModel と UiState

**Files:**
- Create: `feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FriendsUiState.kt`
- Create: `feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FriendsScreenViewModel.kt`
- Test: `feature/friends/src/test/java/blue/starry/onemorecoffee/feature/friends/FriendsScreenViewModelTest.kt`

**Interfaces:**
- Consumes: Task 3 の `SocialRepository` と各モデル
- Produces（Task 9 の UI が使用）:
  - `FriendsUiState`（sealed: `Loading` / `NotJoined` / `Joined(league: League?, ranking: List<RankingEntry>, feed: List<FeedItem>, myUid: String)`）
  - `RankingEntry(rank: Int, member: LeagueMember)` / `FeedItem(event: ActivityEvent, member: LeagueMember?)`
  - `FriendsScreenViewModel`（`uiState: StateFlow<FriendsUiState>`、`errorMessage: StateFlow<String?>`、`isProcessing: StateFlow<Boolean>`、`fun createLeague(leagueName, displayName, emoji)`、`fun joinLeague(inviteCode, displayName, emoji)`、`fun leaveLeague()`、`fun consumeError()`）

- [ ] **Step 1: UiState を実装する（構造のみのため先に作成）**

`feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FriendsUiState.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember

sealed interface FriendsUiState {
    data object Loading : FriendsUiState

    data object NotJoined : FriendsUiState

    data class Joined(
        val league: League?,
        val ranking: List<RankingEntry>,
        val feed: List<FeedItem>,
        val myUid: String,
    ) : FriendsUiState
}

data class RankingEntry(
    val rank: Int,
    val member: LeagueMember,
)

data class FeedItem(
    val event: ActivityEvent,
    // 退会済みメンバーのイベントは member が null になる
    val member: LeagueMember?,
)
```

- [ ] **Step 2: 失敗するテストを書く**

`feature/friends/src/test/java/blue/starry/onemorecoffee/feature/friends/FriendsScreenViewModelTest.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSocialRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSocialRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_noSession_isNotJoined() = runTest {
        val viewModel = FriendsScreenViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(FriendsUiState.NotJoined)
    }

    @Test
    fun uiState_joined_sortsRankingByVisitedCountThenName() = runTest {
        repository.session.value = SocialSession(uid = "me", leagueId = "league1")
        repository.members.value = listOf(
            member(uid = "a", name = "あかり", visited = 10),
            member(uid = "b", name = "いろは", visited = 42),
            member(uid = "c", name = "うみ", visited = 10),
        )
        val viewModel = FriendsScreenViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as FriendsUiState.Joined
        assertThat(state.ranking.map { it.member.uid }).containsExactly("b", "a", "c").inOrder()
        assertThat(state.ranking.map { it.rank }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun uiState_joined_attachesMemberToFeedItem() = runTest {
        repository.session.value = SocialSession(uid = "me", leagueId = "league1")
        repository.members.value = listOf(member(uid = "a", name = "あかり", visited = 1))
        repository.activities.value = listOf(
            ActivityEvent.MemberJoined(id = "a_joined", uid = "a", createdAt = Instant.EPOCH),
            ActivityEvent.MemberJoined(id = "x_joined", uid = "gone", createdAt = Instant.EPOCH),
        )
        val viewModel = FriendsScreenViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as FriendsUiState.Joined
        assertThat(state.feed[0].member?.displayName).isEqualTo("あかり")
        assertThat(state.feed[1].member).isNull()
    }

    @Test
    fun createLeague_failure_exposesErrorMessage() = runTest {
        repository.shouldFailMutations = true
        val viewModel = FriendsScreenViewModel(repository)

        viewModel.createLeague(leagueName = "スタバ部", displayName = "ねぴ", emoji = "☕")
        advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isNotNull()

        viewModel.consumeError()

        assertThat(viewModel.errorMessage.value).isNull()
    }

    private fun member(uid: String, name: String, visited: Int): LeagueMember {
        return LeagueMember(
            uid = uid,
            displayName = name,
            emoji = "☕",
            visitedStoreCount = visited,
            prefectureCount = 1,
            updatedAt = null,
        )
    }

    private class FakeSocialRepository : SocialRepository {
        val session = MutableStateFlow<SocialSession?>(null)
        val league = MutableStateFlow<League?>(null)
        val members = MutableStateFlow(emptyList<LeagueMember>())
        val activities = MutableStateFlow(emptyList<ActivityEvent>())
        var shouldFailMutations = false

        override fun observeSession(): Flow<SocialSession?> = session
        override fun observeLeague(): Flow<League?> = league
        override fun observeMembers(): Flow<List<LeagueMember>> = members
        override fun observeActivities(): Flow<List<ActivityEvent>> = activities

        override suspend fun createLeague(leagueName: String, profile: SocialProfile): League {
            if (shouldFailMutations) throw IllegalStateException("failed")
            return League(id = "new", name = leagueName, inviteCode = "AAAA2222", createdBy = "me")
        }

        override suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League {
            if (shouldFailMutations) throw IllegalStateException("failed")
            return League(id = "joined", name = "スタバ部", inviteCode = inviteCode, createdBy = "other")
        }

        override suspend fun leaveLeague() {
            if (shouldFailMutations) throw IllegalStateException("failed")
            session.value = null
        }

        override suspend fun publishFirstVisits(firstVisits: List<FirstVisit>) = Unit
        override suspend fun refreshOwnStats() = Unit
    }
}
```

- [ ] **Step 3: テストが失敗することを確認する**

Run: `./gradlew :feature:friends:test --tests "blue.starry.onemorecoffee.feature.friends.FriendsScreenViewModelTest"`
Expected: コンパイルエラーで FAIL

- [ ] **Step 4: ViewModel を実装する**

`feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FriendsScreenViewModel.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsScreenViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : ViewModel() {
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val uiState: StateFlow<FriendsUiState> = socialRepository.observeSession()
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(FriendsUiState.NotJoined)
            } else {
                combine(
                    socialRepository.observeLeague(),
                    socialRepository.observeMembers(),
                    socialRepository.observeActivities(),
                ) { league, members, activities ->
                    FriendsUiState.Joined(
                        league = league,
                        ranking = ranking(members),
                        feed = activities.map { event ->
                            FeedItem(event = event, member = members.find { member -> member.uid == event.uid })
                        },
                        myUid = session.uid,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FriendsUiState.Loading,
        )

    init {
        // 参加済みなら画面表示時に自分の統計を最新化する（インポートを介さない訪問記録の反映漏れ対策）
        viewModelScope.launch {
            socialRepository.observeSession().filterNotNull().first()
            runCatching { socialRepository.refreshOwnStats() }
        }
    }

    fun createLeague(leagueName: String, displayName: String, emoji: String) {
        mutate { socialRepository.createLeague(leagueName, SocialProfile(displayName = displayName, emoji = emoji)) }
    }

    fun joinLeague(inviteCode: String, displayName: String, emoji: String) {
        mutate { socialRepository.joinLeague(inviteCode, SocialProfile(displayName = displayName, emoji = emoji)) }
    }

    fun leaveLeague() {
        mutate { socialRepository.leaveLeague() }
    }

    fun consumeError() {
        _errorMessage.value = null
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            runCatching { block() }
                .onFailure { throwable ->
                    _errorMessage.value = throwable.message ?: "処理に失敗しました"
                }
            _isProcessing.value = false
        }
    }

    private fun ranking(members: List<LeagueMember>): List<RankingEntry> {
        return members
            .sortedWith(
                compareByDescending(LeagueMember::visitedStoreCount)
                    .thenBy(LeagueMember::displayName),
            )
            .mapIndexed { index, member -> RankingEntry(rank = index + 1, member = member) }
    }
}
```

- [ ] **Step 5: テストが通ることを確認して Commit**

Run: `./gradlew :feature:friends:test`
Expected: PASS

```bash
git add feature/friends/src
git commit -m "feat: フレンドタブの ViewModel と UiState を追加"
```

---

### Task 9: FriendsScreen の Compose UI

**Files:**
- Create: `feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FriendsScreen.kt`

**Interfaces:**
- Consumes: Task 7 の `formatRelativeTime` / `feedItemText`、Task 8 の `FriendsScreenViewModel` / `FriendsUiState`
- Produces: `@Composable fun FriendsScreen(modifier: Modifier = Modifier, viewModel: FriendsScreenViewModel = hiltViewModel())`（Task 10 の `App.kt` が呼ぶ）

- [ ] **Step 1: FriendsScreen を実装する**

既存 `StatsScreen` の流儀（`hiltViewModel` + `collectAsStateWithLifecycle` + private な Content 関数 + `Card`）に従う。プロジェクトに Compose UI テストの前例がないため、この画面は Task 12 の手動確認で検証する。

`feature/friends/src/main/java/blue/starry/onemorecoffee/feature/friends/FriendsScreen.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant

@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendsScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            confirmButton = {
                TextButton(onClick = viewModel::consumeError) {
                    Text("閉じる")
                }
            },
            text = { Text(message) },
        )
    }

    when (val state = uiState) {
        FriendsUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        FriendsUiState.NotJoined -> NotJoinedContent(
            isProcessing = isProcessing,
            onCreate = viewModel::createLeague,
            onJoin = viewModel::joinLeague,
            modifier = modifier,
        )
        is FriendsUiState.Joined -> JoinedContent(
            state = state,
            onLeave = viewModel::leaveLeague,
            modifier = modifier,
        )
    }
}

@Composable
private fun NotJoinedContent(
    isProcessing: Boolean,
    onCreate: (leagueName: String, displayName: String, emoji: String) -> Unit,
    onJoin: (inviteCode: String, displayName: String, emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("☕") }
    var leagueName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "フレンドとリーグを組んで、制覇の進み具合を共有しよう",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("表示名") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = emoji,
            onValueChange = { emoji = it },
            label = { Text("アイコン絵文字") },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        OutlinedTextField(
            value = leagueName,
            onValueChange = { leagueName = it },
            label = { Text("新しいリーグの名前") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onCreate(leagueName, displayName, emoji) },
            enabled = !isProcessing && leagueName.isNotBlank() && displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("リーグを作成")
        }
        HorizontalDivider()
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("招待コード") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { onJoin(inviteCode, displayName, emoji) },
            enabled = !isProcessing && inviteCode.isNotBlank() && displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("招待コードで参加")
        }
    }
}

@Composable
private fun JoinedContent(
    state: FriendsUiState.Joined,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsLeaveDialog by remember { mutableStateOf(false) }

    if (showsLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showsLeaveDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showsLeaveDialog = false
                        onLeave()
                    },
                ) {
                    Text("退出する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showsLeaveDialog = false }) {
                    Text("キャンセル")
                }
            },
            text = { Text("リーグを退出すると、自分のアクティビティと統計はリーグから削除されます。") },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.league?.name ?: "リーグ",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "招待コード: ${state.league?.inviteCode ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "総制覇数ランキング",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.ranking.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${entry.rank}. ${entry.member.emoji} ${entry.member.displayName}" +
                                    if (entry.member.uid == state.myUid) "（自分）" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${entry.member.visitedStoreCount} 店舗",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "アクティビティ",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(state.feed, key = { item -> item.event.id }) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = feedItemText(
                        event = item.event,
                        memberName = item.member?.displayName ?: "元メンバー",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatRelativeTime(createdAt = item.event.createdAt, now = Instant.now()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TextButton(onClick = { showsLeaveDialog = true }) {
                Text("リーグを退出", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

- [ ] **Step 2: ビルド確認と Commit**

Run: `./gradlew :feature:friends:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

```bash
git add feature/friends/src
git commit -m "feat: フレンドタブの画面を追加"
```

---

### Task 10: アプリへのタブ統合

**Files:**
- Create: `app/src/main/res/drawable/group.xml`
- Modify: `app/src/main/java/blue/starry/onemorecoffee/Route.kt`
- Modify: `app/src/main/java/blue/starry/onemorecoffee/App.kt`

**Interfaces:**
- Consumes: Task 9 の `FriendsScreen`
- Produces: ボトムナビ 4 タブ目「フレンド」

- [ ] **Step 1: タブアイコンを追加する**

`app/src/main/res/drawable/group.xml`（Material Symbols の group アイコン。既存アイコンと同じ 960 viewport 形式。レンダリングが崩れる場合は https://fonts.google.com/icons から group を Android Vector 形式で再取得して置き換えること）:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:height="24dp" android:tint="#000000" android:viewportHeight="960" android:viewportWidth="960" android:width="24dp">

    <path android:fillColor="@android:color/white" android:pathData="M40,800v-112q0,-34 17.5,-62.5T104,582q62,-31 126,-46.5T360,520q66,0 130,15.5T616,582q29,15 46.5,43.5T680,688v112L40,800ZM760,800v-120q0,-44 -24.5,-84.5T666,526q51,6 96,20.5t84,35.5q36,20 55,44.5t19,53.5v120L760,800ZM360,480q-66,0 -113,-47t-47,-113q0,-66 47,-113t113,-47q66,0 113,47t47,113q0,66 -47,113t-113,47ZM760,320q0,66 -47,113t-113,47q-11,0 -28,-2.5t-28,-5.5q27,-32 41.5,-71t14.5,-81q0,-42 -14.5,-81T544,168q14,-5 28,-6.5t28,-1.5q66,0 113,47t47,113ZM120,720h480v-32q0,-11 -5.5,-20T580,654q-54,-27 -109,-40.5T360,600q-56,0 -111,13.5T140,654q-9,5 -14.5,14t-5.5,20v32ZM360,400q33,0 56.5,-23.5T440,320q0,-33 -23.5,-56.5T360,240q-33,0 -56.5,23.5T280,320q0,33 23.5,56.5T360,400ZM360,720ZM360,320Z"/>

</vector>
```

- [ ] **Step 2: Route にフレンドタブを追加する**

`Route.kt` を次に置き換え:

```kotlin
package blue.starry.onemorecoffee

enum class Route(
    val label: String,
    val iconResId: Int,
) {
    Map("マップ", R.drawable.map_search),
    List("リスト", R.drawable.checklist),
    Stats("統計", R.drawable.summarize),
    Friends("フレンド", R.drawable.group),
    Settings("設定", R.drawable.settings),
    ;

    companion object {
        val bottomTabs = listOf(Map, List, Stats, Friends)
    }
}
```

- [ ] **Step 3: App.kt に画面分岐を追加する**

`App.kt` の import に追加:

```kotlin
import blue.starry.onemorecoffee.feature.friends.FriendsScreen
```

`when (currentRoute)` ブロックの `Route.Stats -> ...` の後に追加:

```kotlin
                    Route.Friends -> FriendsScreen(modifier = Modifier.fillMaxSize())
```

- [ ] **Step 4: ビルド確認と Commit**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

```bash
git add app/src
git commit -m "feat: ボトムナビにフレンドタブを追加"
```

---

### Task 11: Firestore セキュリティルールの整備とデプロイ

**Files:**
- Create: `firebase/firestore.rules`
- Create: `firebase/firebase.json`
- Modify: `README.md`（セットアップ手順の追記）

**Interfaces:**
- Consumes: Task 0 の Firebase プロジェクト
- Produces: デプロイ済みセキュリティルール（Task 12 の E2E が前提とする）

- [ ] **Step 1: ルールファイルを作成する**

`firebase/firestore.rules`:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() {
      return request.auth != null;
    }
    function isMember(leagueId) {
      return isSignedIn()
        && exists(/databases/$(database)/documents/leagues/$(leagueId)/members/$(request.auth.uid));
    }
    function isCreator(leagueId) {
      return isSignedIn()
        && get(/databases/$(database)/documents/leagues/$(leagueId)).data.createdBy == request.auth.uid;
    }

    // 招待コード: get のみ許可し、list を拒否してコードの列挙を防ぐ
    match /inviteCodes/{code} {
      allow get: if isSignedIn();
      allow list: if false;
      allow create: if isSignedIn();
      allow update, delete: if false;
    }

    match /leagues/{leagueId} {
      allow get: if isMember(leagueId);
      allow list: if false;
      allow create: if isSignedIn() && request.resource.data.createdBy == request.auth.uid;
      allow update, delete: if false;

      match /members/{uid} {
        allow read: if isMember(leagueId);
        // 参加 = 自分の uid の member doc を作ること（招待コードを知っている前提の soft security）
        allow create, update: if isSignedIn() && request.auth.uid == uid;
        // 作成者は端末紛失などで孤児化したメンバーを掃除できる
        allow delete: if isSignedIn() && (request.auth.uid == uid || isCreator(leagueId));
      }

      match /activities/{activityId} {
        allow read: if isMember(leagueId);
        allow create, update: if isMember(leagueId) && request.resource.data.uid == request.auth.uid;
        allow delete: if isSignedIn() && (resource.data.uid == request.auth.uid || isCreator(leagueId));
      }
    }
  }
}
```

- [ ] **Step 2: firebase.json を作成する**

`firebase/firebase.json`:

```json
{
  "firestore": {
    "rules": "firestore.rules"
  }
}
```

- [ ] **Step 3: ルールをデプロイする（人間の作業）**

実装エージェントはユーザーに次の実行を依頼する:

```bash
cd ~/ghq/github.com/SlashNephy/OneMoreCoffee/firebase
npx firebase-tools login
npx firebase-tools deploy --only firestore:rules --project <FIREBASE_PROJECT_ID>
```

Expected: `Deploy complete!`

- [ ] **Step 4: README にセットアップ手順を追記する**

`README.md` の末尾（または既存のセットアップ節）に追記:

```markdown
## ソーシャル機能のセットアップ（任意）

フレンド機能（リーグ）を使う場合のみ必要。未設定でもアプリ本体は動作する。

1. Firebase プロジェクトを作成し、Android アプリ（`blue.starry.onemorecoffee` と `blue.starry.onemorecoffee.debug`）を追加する
2. Firestore（`asia-northeast1`）と Authentication の匿名サインインを有効化する
3. `secrets.properties` に `FIREBASE_PROJECT_ID` / `FIREBASE_APPLICATION_ID` / `FIREBASE_API_KEY` を記入する
4. セキュリティルールをデプロイする: `cd firebase && npx firebase-tools deploy --only firestore:rules`
```

- [ ] **Step 5: Commit**

```bash
git add firebase README.md
git commit -m "feat: Firestore セキュリティルールとセットアップ手順を追加"
```

---

### Task 12: 手動 E2E 確認（2 プロファイルでの実機確認）

**Files:** なし（検証のみ。発見した不具合は個別に修正・コミットする）

**Interfaces:**
- Consumes: ここまでの全タスク + デプロイ済みルール

- [ ] **Step 1: 全テストとビルドの最終確認**

Run: `./gradlew test assembleDebug lint`
Expected: すべて成功

- [ ] **Step 2: エミュレータ 2 台で E2E を実施する**

Android エミュレータを 2 つ起動し（例: `Pixel_9` と `Pixel_9_Pro`）、両方に debug APK をインストールして次を確認する:

1. 端末 A: フレンドタブ → 表示名「A」を入れてリーグ「スタバ部」を作成 → 招待コードが表示される
2. 端末 A: フィードに「A さんがリーグに参加」が出る
3. 端末 B: フレンドタブ → 端末 A の招待コードで参加（表示名「B」）
4. 端末 A: フィードに「B さんがリーグに参加」がほぼ即時に現れる（リアルタイム性の確認）
5. どちらかの端末で My Starbucks インポートを実行（または Room に訪問データがある状態で参加）→ BACKFILL がフィードに出る、ランキングの店舗数が更新される
6. 機内モードにした端末でアプリを再起動してもフィードが表示される（オフラインキャッシュの確認）
7. 端末 B: リーグを退出 → 端末 A のフィードから B のイベントが消え、ランキングから B が消える
8. `secrets.properties` の Firebase 値を消して再ビルドした APK で、フレンドタブが「未参加」表示のまま、作成/参加でエラーダイアログが出る（未構成時のフォールバック確認）

- [ ] **Step 3: スクリーンショットを撮る**

上記 4（フィード）と 5（ランキング）の画面をスクリーンショット保存する（PR 添付用。DroidKaigi スライド素材にもなる）。

```bash
adb exec-out screencap -p > /tmp/friends_feed.png
adb exec-out screencap -p > /tmp/friends_ranking.png
```

---

### Task 13: 本体設計書（docs/design.md）の改訂

**Files:**
- Modify: `docs/design.md`

**Interfaces:**
- Consumes: 設計書 §6 の改訂方針表

- [ ] **Step 1: docs/design.md を改訂する**

次の 5 箇所を変更する:

1. §1.3 想定ユーザー: 「開発者本人。」を「開発者本人と、限定配布を受けた少数の友人。」に変更。「Firebase Distribution や GitHub Actions による配布機能も不要。」を「配布は Firebase App Distribution 等の招待制の限定配布に限る（公開配布は行わない）。」に変更
2. §2.3 スコープ外: 「端末間同期（個人利用前提のため）」の行を削除し、代わりに「なお、フレンド間のソーシャル共有（リーグ）は 2026-07 に追加された。詳細は `docs/superpowers/specs/2026-07-07-social-feature-design.md` を参照」を §2.2 の末尾に追記
3. §3.3 プライバシー: 「外部送信は行わない（クラッシュレポートを除く）」を「外部送信は行わない（クラッシュレポートと、ユーザーが明示的に有効化したソーシャル機能を除く）。ソーシャル機能で送信されるのは店舗 ID、店舗名、都道府県、訪問日、集計統計のみ」に変更
4. §7.1 画面構成: タブ列挙を「[マップ] [リスト] [統計] [フレンド] + 設定（トップバー）」に変更
5. §10.1 検討事項 4（タブ構成）: 「4タブで足りるか、「通知フィード」を追加するか」を「解決済み: フレンドタブとして追加（2026-07）」に変更

- [ ] **Step 2: Commit**

```bash
git add docs/design.md
git commit -m "docs: ソーシャル機能の追加に伴い設計書を改訂"
```

---

### Task 14: プルリクエストの作成

**Files:** なし

- [ ] **Step 1: push して PR を作成する**

```bash
git push -u origin feature/social-league-phase1
gh pr create --repo SlashNephy/OneMoreCoffee \
  --title "feat: ソーシャル機能フェーズ1（リーグ・アクティビティフィード・ランキング）" \
  --body "$(cat <<'EOF'
## 概要

フレンド数人と使う「リーグ」機能のフェーズ 1 (MVP) を実装した。
設計書: `docs/superpowers/specs/2026-07-07-social-feature-design.md`

- リーグの作成・招待コード参加・退出
- アクティビティフィード（初訪問 / 一括登録 / メンバー参加、50 件、リアルタイム更新）
- 総制覇数ランキング（クライアント側ソート、統計は自己申告方式）
- Firebase (Firestore + Anonymous Auth) を Spark プランの範囲で使用。Room が原本でサーバーは導出データのみ

## 変更点

- `core:social` 新設: Firestore 実装・DataStore セッション永続化・Firebase 手動初期化（google-services プラグイン不使用）
- `feature:friends` 新設: フレンドタブ UI
- `core:domain`: SocialRepository とモデル群を追加
- `core:data`: インポート時の初訪問検出と publish フック（失敗してもインポートは成功する）
- `firebase/`: セキュリティルール
- ボトムナビに 4 つ目のタブ「フレンド」を追加

## 動作確認

- `./gradlew test assembleDebug lint` パス
- エミュレータ 2 台でリーグ作成 → 参加 → インポート → フィード/ランキング反映 → 退出を確認
- Firebase 未構成ビルドでアプリ本体が従来どおり動作することを確認

## 動物界における比擬

この変更は、単独で縄張りを巡回していたミツバチが、仲間と 8 の字ダンスで蜜源の場所を伝え合うようになったことに相当します。各ミツバチ（端末）は自分の記憶（Room）だけを頼りに飛び回る点は変わりませんが、巣（Firestore のリーグ）に戻ると「どの花畑（店舗）をいつ訪れたか」を仲間に踊って見せ、巣の壁には群れ全体の採蜜成績（ランキング）が掲示されます。巣が壊れても各ミツバチの記憶は無事で、新しい巣を作ればまた踊り直せる、という点まで含めて忠実な比擬です。

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr edit --repo SlashNephy/OneMoreCoffee --add-assignee SlashNephy
```

- [ ] **Step 2: スクリーンショットを PR に添付する**

`github-image-upload` スキルを使い、Task 12 Step 3 のスクリーンショット 2 枚を PR 本文の「動作確認」節に埋め込む。

---

## 実装順序と依存関係

```
Task 0 (人間: Firebase 準備)
  → Task 1 (モジュール骨格) → Task 2 (Firebase 初期化)
  → Task 3 (domain) → Task 4 (マッパー) → Task 5 (Repository 実装)
  → Task 6 (core/data フック)   ※ Task 3 のみに依存（Task 4/5 と並行可）
  → Task 7 (整形) → Task 8 (ViewModel) → Task 9 (UI) → Task 10 (タブ統合)
  → Task 11 (ルール) → Task 12 (手動 E2E) → Task 13 (docs) → Task 14 (PR)
```

## フェーズ 1 でやらないこと（設計書 §7 のフェーズ 2 以降）

- MILESTONE イベント、月間新規制覇レース、絵文字リアクション、App Check
- FCM プッシュ通知、Google アカウントリンク
- Firestore Emulator による自動統合テスト（オープンクエスチョン。当面は Task 12 の手動 E2E で担保）
