# OneMoreCoffee MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the OneMoreCoffee Android MVP that imports Starbucks visit history, stores current store master data locally, and shows visit progress on map/list/stats/settings screens.

**Architecture:** Create a Kotlin Android multi-module app inspired by Mitsubachi: `feature:*` modules depend on `core:domain` and `core:ui`, while Room/Ktor/WebView parsing live in `core:data`. The MVP keeps store rows only for current store-master entries, stores visits independently by official `store_id`, and joins them in repository/use case projections for UI.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Navigation 3, Hilt, Room, Ktor, kotlinx.serialization, Google Maps Compose, Coroutines/Flow, JUnit, Robolectric where needed.

---

## File Structure

Create these project files first:

- `settings.gradle.kts`: Gradle plugin management, repositories, module includes.
- `build.gradle.kts`: root plugin aliases and shared cleanup tasks.
- `gradle/libs.versions.toml`: Renovate-readable dependency versions.
- `gradle.properties`: AndroidX, Kotlin, Gradle, Compose flags.
- `mise.toml`: toolchain entry for Java.
- `renovate.json`: dependency update configuration.
- `.gitignore`: Gradle/Android/local secrets ignores.
- `secrets.defaults.properties`: default placeholder Maps API key.
- `secrets.properties`: local-only API key file, ignored.

Create these modules:

- `app`: Android application, activity, app navigation shell, Hilt application.
- `core/common`: date/time serializers and parsing helpers.
- `core/domain`: domain models, repository interfaces, use cases, UI-ready projections.
- `core/data`: Room database, Ktor Starbucks store client, import parser, repository implementations.
- `core/ui`: OneMoreCoffee theme and shared Compose components.
- `feature/map`: Google Maps screen and store detail bottom sheet.
- `feature/list`: searchable/filterable store list.
- `feature/stats`: national MVP statistics screen.
- `feature/settings`: data update/import/logout entry points.
- `feature/import`: WebView screen for My Starbucks import.

Keep comments in Japanese unless surrounding generated or Android framework code is conventionally English. Logs and user-facing technical error messages should be English.

---

### Task 1: Bootstrap Android Gradle Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `mise.toml`
- Create: `renovate.json`
- Create: `.gitignore`
- Create: `secrets.defaults.properties`
- Create: `secrets.properties`

- [ ] **Step 1: Add toolchain metadata**

Create `mise.toml`:

```toml
[tools]
java = "temurin-21"
```

- [ ] **Step 2: Add ignores**

Create `.gitignore`:

```gitignore
.gradle/
.idea/
.kotlin/
.vscode/
build/
local.properties
secrets.properties
*.iml
captures/
```

- [ ] **Step 3: Add Maps API defaults**

Create `secrets.defaults.properties`:

```properties
MAPS_API_KEY=DEFAULT_API_KEY
```

Create local `secrets.properties`:

```properties
MAPS_API_KEY=
```

Do not commit `secrets.properties`.

- [ ] **Step 4: Add Gradle properties**

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 5: Add version catalog**

Create `gradle/libs.versions.toml` with these entries:

```toml
[versions]
android-gradle-plugin = "9.0.1"
kotlin = "2.2.21"
ksp = "2.3.7"
androidx-activity = "1.13.0"
androidx-compose-bom = "2026.04.00"
androidx-compose-material3 = "1.5.0-alpha18"
androidx-core-ktx = "1.18.0"
androidx-lifecycle = "2.10.0"
androidx-navigation3 = "1.1.1"
androidx-room = "2.8.4"
androidx-test-ext-junit = "1.3.0"
hilt = "2.59.2"
androidx-hilt = "1.3.0"
ktor = "3.4.3"
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.11.0"
android-maps-compose = "8.3.0"
play-services-maps = "20.0.0"
play-services-location = "21.3.0"
mapsplatform-secrets = "2.0.1"
junit = "4.13.2"
robolectric = "4.16"
truth = "1.4.5"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core-ktx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-compose-bom = { module = "androidx.compose:compose-bom-alpha", version.ref = "androidx-compose-bom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "androidx-compose-material3" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "androidx-navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "androidx-navigation3" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "androidx-room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "androidx-room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-android-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidx-hilt" }
ktor-bom = { module = "io.ktor:ktor-bom", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
android-maps-compose = { module = "com.google.maps.android:maps-compose", version.ref = "android-maps-compose" }
android-maps-compose-utils = { module = "com.google.maps.android:maps-compose-utils", version.ref = "android-maps-compose" }
play-services-maps = { module = "com.google.android.gms:play-services-maps", version.ref = "play-services-maps" }
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "play-services-location" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext-junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }

[plugins]
android-application = { id = "com.android.application", version.ref = "android-gradle-plugin" }
android-library = { id = "com.android.library", version.ref = "android-gradle-plugin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
mapsplatform-secrets = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "mapsplatform-secrets" }
```

- [ ] **Step 6: Add settings**

Create `settings.gradle.kts`:

```kotlin
@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "OneMoreCoffee"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
  ":app",
  ":core:common",
  ":core:domain",
  ":core:data",
  ":core:ui",
  ":feature:map",
  ":feature:list",
  ":feature:stats",
  ":feature:settings",
  ":feature:import",
)
```

- [ ] **Step 7: Add root Gradle build**

Create `build.gradle.kts`:

```kotlin
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.mapsplatform.secrets) apply false
}
```

- [ ] **Step 8: Add Renovate config**

Create `renovate.json`:

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:recommended"],
  "timezone": "Asia/Tokyo",
  "labels": ["dependencies"]
}
```

- [ ] **Step 9: Verify Gradle can resolve project structure**

Run:

```bash
mise exec -- ./gradlew projects
```

Expected: Gradle wrapper may be missing. If missing, copy `gradlew`, `gradlew.bat`, and `gradle/wrapper/` from `~/ghq/github.com/SlashNephy/mitsubachi`, then run the command again. Expected final result: all declared modules appear.

- [ ] **Step 10: Run pre-commit verification**

Run:

```bash
mise exec -- ./gradlew projects
```

Expected: success.

- [ ] **Step 11: Commit**

```bash
git add .gitignore build.gradle.kts gradle gradle.properties gradlew gradlew.bat mise.toml renovate.json secrets.defaults.properties settings.gradle.kts
git commit -m "chore: Androidプロジェクトの土台を追加"
```

---

### Task 2: Create App and Core Module Skeletons

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/blue/starry/onemorecoffee/OneMoreCoffeeApplication.kt`
- Create: `app/src/main/java/blue/starry/onemorecoffee/MainActivity.kt`
- Create: `app/src/main/java/blue/starry/onemorecoffee/App.kt`
- Create: `core/common/build.gradle.kts`
- Create: `core/domain/build.gradle.kts`
- Create: `core/data/build.gradle.kts`
- Create: `core/ui/build.gradle.kts`
- Create: `feature/map/build.gradle.kts`
- Create: `feature/list/build.gradle.kts`
- Create: `feature/stats/build.gradle.kts`
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/import/build.gradle.kts`
- Create: `core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/OneMoreCoffeeTheme.kt`

- [ ] **Step 1: Add module build files**

Use this pattern for Compose feature modules:

```kotlin
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
}

android {
  namespace = "blue.starry.onemorecoffee.feature.map"
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
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)
}
```

Change `namespace` per module.

- [ ] **Step 2: Add core module build files**

`core/domain/build.gradle.kts`:

```kotlin
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "blue.starry.onemorecoffee.core.domain"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
  }
}

dependencies {
  implementation(libs.kotlinx.coroutines.android)
}
```

`core/common/build.gradle.kts`:

```kotlin
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "blue.starry.onemorecoffee.core.common"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
  }
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
}
```

`core:data/build.gradle.kts`:

```kotlin
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
}

android {
  namespace = "blue.starry.onemorecoffee.core.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
  }
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.domain)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(platform(libs.ktor.bom))
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)
  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.android.compiler)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.androidx.room.testing)
}
```

- [ ] **Step 3: Add app build file**

Create `app/build.gradle.kts`:

```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mapsplatform.secrets)
}

android {
  namespace = "blue.starry.onemorecoffee"
  compileSdk = 36

  defaultConfig {
    applicationId = "blue.starry.onemorecoffee"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }
}

secrets {
  propertiesFileName = rootProject.relativePath("secrets.properties")
  defaultPropertiesFileName = rootProject.relativePath("secrets.defaults.properties")
}

dependencies {
  implementation(projects.core.data)
  implementation(projects.core.domain)
  implementation(projects.core.ui)
  implementation(projects.feature.map)
  implementation(projects.feature.list)
  implementation(projects.feature.stats)
  implementation(projects.feature.settings)
  implementation(projects.feature.import)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 4: Add app entry files**

`app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

  <application
    android:name=".OneMoreCoffeeApplication"
    android:allowBackup="true"
    android:theme="@style/AppTheme">
    <meta-data
      android:name="com.google.android.geo.API_KEY"
      android:value="${MAPS_API_KEY}" />

    <activity
      android:name=".MainActivity"
      android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
```

`OneMoreCoffeeApplication.kt`:

```kotlin
package blue.starry.onemorecoffee

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OneMoreCoffeeApplication : Application()
```

`MainActivity.kt`:

```kotlin
package blue.starry.onemorecoffee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import blue.starry.onemorecoffee.core.ui.OneMoreCoffeeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      OneMoreCoffeeTheme {
        App()
      }
    }
  }
}
```

- [ ] **Step 5: Add basic theme and app shell**

`core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/OneMoreCoffeeTheme.kt`:

```kotlin
package blue.starry.onemorecoffee.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OneMoreCoffeeColorScheme = lightColorScheme(
  primary = Color(0xFF006241),
  secondary = Color(0xFFC98A3B),
  tertiary = Color(0xFF2E5C8A),
  background = Color(0xFFF8F7F4),
  surface = Color.White,
)

@Composable
fun OneMoreCoffeeTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = OneMoreCoffeeColorScheme,
    content = content,
  )
}
```

`app/src/main/java/blue/starry/onemorecoffee/App.kt`:

```kotlin
package blue.starry.onemorecoffee

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun App() {
  Text("OneMoreCoffee")
}
```

- [ ] **Step 6: Verify**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: success or lint errors only for missing app theme resource. If the manifest theme fails, add `app/src/main/res/values/themes.xml` with a basic style and rerun.

- [ ] **Step 7: Commit**

```bash
git add app core feature
git commit -m "chore: Androidアプリのモジュール構成を追加"
```

---

### Task 3: Domain Models and Use Cases

**Files:**
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/Store.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/Visit.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/StoreVisitSummary.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/model/ProgressStats.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/repository/StoreRepository.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/repository/VisitRepository.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/usecase/ObserveStoreSummariesUseCase.kt`
- Create: `core/domain/src/main/java/blue/starry/onemorecoffee/core/domain/usecase/ObserveProgressStatsUseCase.kt`
- Test: `core/domain/src/test/java/blue/starry/onemorecoffee/core/domain/usecase/ObserveProgressStatsUseCaseTest.kt`

- [ ] **Step 1: Write stats test**

Create `ObserveProgressStatsUseCaseTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.usecase

import blue.starry.onemorecoffee.core.domain.model.ProgressStats
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlin.test.Test

class ObserveProgressStatsUseCaseTest {
  @Test
  fun calculate_usesOnlyCurrentStoreSummaries() {
    val summaries = listOf(
      StoreVisitSummary(
        id = "1001",
        name = "丸の内オアゾ店",
        prefecture = "東京都",
        fullAddress = "東京都千代田区",
        latitude = 35.0,
        longitude = 139.0,
        isReserve = false,
        visitCount = 1,
        lastVisitedOn = LocalDate.of(2026, 5, 1),
      ),
      StoreVisitSummary(
        id = "1002",
        name = "未訪問店",
        prefecture = "東京都",
        fullAddress = "東京都千代田区",
        latitude = 35.1,
        longitude = 139.1,
        isReserve = false,
        visitCount = 0,
        lastVisitedOn = null,
      ),
    )

    val result = ProgressStats.from(summaries)

    assertThat(result).isEqualTo(
      ProgressStats(
        totalStores = 2,
        visitedStores = 1,
        completionRate = 0.5,
      ),
    )
  }
}
```

- [ ] **Step 2: Run failing test**

Run:

```bash
mise exec -- ./gradlew :core:domain:test --tests '*ObserveProgressStatsUseCaseTest'
```

Expected: fail because domain models do not exist.

- [ ] **Step 3: Add domain models**

`Store.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

data class Store(
  val id: String,
  val name: String,
  val nameEn: String?,
  val prefCode: String,
  val prefecture: String,
  val fullAddress: String,
  val latitude: Double,
  val longitude: Double,
  val isReserve: Boolean,
)
```

`Visit.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import java.time.LocalDate

data class Visit(
  val id: Long,
  val storeId: String,
  val visitedOn: LocalDate,
  val source: VisitSource,
)

enum class VisitSource {
  IMPORTED_STARBUCKS,
}
```

`StoreVisitSummary.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

import java.time.LocalDate

data class StoreVisitSummary(
  val id: String,
  val name: String,
  val prefecture: String,
  val fullAddress: String,
  val latitude: Double,
  val longitude: Double,
  val isReserve: Boolean,
  val visitCount: Int,
  val lastVisitedOn: LocalDate?,
) {
  val isVisited: Boolean
    get() = visitCount > 0
}
```

`ProgressStats.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.model

data class ProgressStats(
  val totalStores: Int,
  val visitedStores: Int,
  val completionRate: Double,
) {
  companion object {
    fun from(summaries: List<StoreVisitSummary>): ProgressStats {
      val totalStores = summaries.size
      val visitedStores = summaries.count { it.isVisited }
      val completionRate = if (totalStores == 0) {
        0.0
      } else {
        visitedStores.toDouble() / totalStores
      }

      return ProgressStats(
        totalStores = totalStores,
        visitedStores = visitedStores,
        completionRate = completionRate,
      )
    }
  }
}
```

- [ ] **Step 4: Add repository interfaces and use cases**

`StoreRepository.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.repository

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
  fun observeStoreSummaries(): Flow<List<StoreVisitSummary>>
  suspend fun refreshStores(): StoreRefreshResult
}

data class StoreRefreshResult(
  val upserted: Int,
  val skipped: Int,
)
```

`VisitRepository.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.repository

interface VisitRepository {
  suspend fun importStarbucksVisits(json: String): VisitImportResult
  suspend fun logoutImporter()
}

data class VisitImportResult(
  val inserted: Int,
  val duplicated: Int,
  val unknownStoreVisits: Int,
  val failed: Int,
)
```

`ObserveStoreSummariesUseCase.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.usecase

import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import javax.inject.Inject

class ObserveStoreSummariesUseCase @Inject constructor(
  private val storeRepository: StoreRepository,
) {
  operator fun invoke() = storeRepository.observeStoreSummaries()
}
```

`ObserveProgressStatsUseCase.kt`:

```kotlin
package blue.starry.onemorecoffee.core.domain.usecase

import blue.starry.onemorecoffee.core.domain.model.ProgressStats
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class ObserveProgressStatsUseCase @Inject constructor(
  private val storeRepository: StoreRepository,
) {
  operator fun invoke() = storeRepository.observeStoreSummaries().map(ProgressStats::from)
}
```

- [ ] **Step 5: Run test**

Run:

```bash
mise exec -- ./gradlew :core:domain:test --tests '*ObserveProgressStatsUseCaseTest'
```

Expected: pass.

- [ ] **Step 6: Run pre-commit verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add core/domain
git commit -m "feat: 店舗と訪問履歴のドメインモデルを追加"
```

---

### Task 4: Room Database for Stores and Visits

**Files:**
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/OneMoreCoffeeDatabase.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/entity/StoreEntity.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/entity/VisitEntity.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/dao/StoreDao.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/dao/VisitDao.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/database/Converters.kt`
- Test: `core/data/src/test/java/blue/starry/onemorecoffee/core/data/database/VisitDaoTest.kt`

- [ ] **Step 1: Write unique-index test**

Create `VisitDaoTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class VisitDaoTest {
  private lateinit var database: OneMoreCoffeeDatabase

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      OneMoreCoffeeDatabase::class.java,
    ).build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun insertIgnore_ignoresSameStoreAndDateEvenWhenSourceMatches() = runTest {
    val visit = VisitEntity(
      storeId = "1369",
      visitedOn = LocalDate.of(2026, 4, 28),
      source = VisitSource.IMPORTED_STARBUCKS,
    )

    val first = database.visitDao().insertIgnore(visit)
    val second = database.visitDao().insertIgnore(visit)

    assertThat(first).isNotEqualTo(-1)
    assertThat(second).isEqualTo(-1)
    assertThat(database.visitDao().count()).isEqualTo(1)
  }
}
```

- [ ] **Step 2: Run failing test**

Run:

```bash
mise exec -- ./gradlew :core:data:test --tests '*VisitDaoTest'
```

Expected: fail because Room classes do not exist.

- [ ] **Step 3: Add Room entities and converters**

`StoreEntity.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "stores")
data class StoreEntity(
  @PrimaryKey val id: String,
  val name: String,
  val nameEn: String?,
  val prefCode: String,
  val prefecture: String,
  val fullAddress: String,
  val latitude: Double,
  val longitude: Double,
  val isReserve: Boolean,
  val rawJson: String,
  val lastSeenAt: Instant,
)
```

`VisitEntity.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import java.time.LocalDate

@Entity(
  tableName = "visits",
  indices = [
    Index(value = ["storeId", "visitedOn"], unique = true),
  ],
)
data class VisitEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val storeId: String,
  val visitedOn: LocalDate,
  val source: VisitSource,
)
```

`Converters.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database

import androidx.room.TypeConverter
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import java.time.Instant
import java.time.LocalDate

class Converters {
  @TypeConverter
  fun instantToString(value: Instant?): String? = value?.toString()

  @TypeConverter
  fun stringToInstant(value: String?): Instant? = value?.let(Instant::parse)

  @TypeConverter
  fun localDateToString(value: LocalDate?): String? = value?.toString()

  @TypeConverter
  fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

  @TypeConverter
  fun visitSourceToString(value: VisitSource?): String? = value?.name

  @TypeConverter
  fun stringToVisitSource(value: String?): VisitSource? = value?.let(VisitSource::valueOf)
}
```

- [ ] **Step 4: Add DAOs and database**

`VisitDao.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity

@Dao
interface VisitDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnore(visit: VisitEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnore(visits: List<VisitEntity>): List<Long>

  @Query("SELECT COUNT(*) FROM visits")
  suspend fun count(): Int

  @Query("DELETE FROM visits")
  suspend fun deleteAll()
}
```

`StoreDao.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
  @Upsert
  suspend fun upsertAll(stores: List<StoreEntity>)

  @Query("DELETE FROM stores WHERE id NOT IN (:ids)")
  suspend fun deleteStoresNotIn(ids: List<String>)

  @Query("SELECT * FROM stores ORDER BY prefCode, name")
  fun observeAll(): Flow<List<StoreEntity>>

  @Query("SELECT id FROM stores")
  suspend fun ids(): List<String>
}
```

`OneMoreCoffeeDatabase.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity

@Database(
  entities = [
    StoreEntity::class,
    VisitEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OneMoreCoffeeDatabase : RoomDatabase() {
  abstract fun storeDao(): StoreDao
  abstract fun visitDao(): VisitDao
}
```

- [ ] **Step 5: Run Room test**

Run:

```bash
mise exec -- ./gradlew :core:data:test --tests '*VisitDaoTest'
```

Expected: pass.

- [ ] **Step 6: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add core/data
git commit -m "feat: 店舗と訪問履歴のRoomスキーマを追加"
```

---

### Task 5: Starbucks Store Master Client and Parser

**Files:**
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/starbucks/StarbucksStoreClient.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/starbucks/CloudSearchResponse.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/starbucks/StoreFieldMapper.kt`
- Test: `core/data/src/test/java/blue/starry/onemorecoffee/core/data/starbucks/StoreFieldMapperTest.kt`

- [ ] **Step 1: Write parser tests**

Create `StoreFieldMapperTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.starbucks

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class StoreFieldMapperTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun toEntity_usesWgs84LocationAndRawJson() {
    val fields = json.parseToJsonElement(
      """
      {
        "store_id": ["1369"],
        "name": ["渋谷2丁目店"],
        "en_name": ["Shibuya 2-chome"],
        "pref_code": ["13"],
        "address_1": ["東京都"],
        "address_2": ["渋谷区"],
        "address_3": ["渋谷2-9-8"],
        "location": ["35.658034,139.703535"],
        "location_jp": ["35.661260,139.700300"],
        "reserve_flg": ["0"]
      }
      """.trimIndent(),
    ).jsonObject

    val entity = StoreFieldMapper.toEntity(fields, rawJson = fields.toString())

    assertThat(entity?.id).isEqualTo("1369")
    assertThat(entity?.latitude).isEqualTo(35.658034)
    assertThat(entity?.longitude).isEqualTo(139.703535)
    assertThat(entity?.prefecture).isEqualTo("東京都")
    assertThat(entity?.fullAddress).contains("渋谷2-9-8")
  }

  @Test
  fun toEntity_returnsNullWhenLocationIsMissing() {
    val fields = json.parseToJsonElement(
      """
      {
        "store_id": ["9999"],
        "name": ["座標なし店"],
        "pref_code": ["13"]
      }
      """.trimIndent(),
    ).jsonObject

    assertThat(StoreFieldMapper.toEntity(fields, rawJson = fields.toString())).isNull()
  }
}
```

- [ ] **Step 2: Run failing test**

Run:

```bash
mise exec -- ./gradlew :core:data:test --tests '*StoreFieldMapperTest'
```

Expected: fail because mapper does not exist.

- [ ] **Step 3: Add CloudSearch DTO**

`CloudSearchResponse.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.starbucks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CloudSearchResponse(
  val hits: Hits,
) {
  @Serializable
  data class Hits(
    val found: Int,
    val start: Int,
    val hit: List<Hit> = emptyList(),
  )

  @Serializable
  data class Hit(
    val id: String,
    @SerialName("fields")
    val fields: JsonObject,
  )
}
```

- [ ] **Step 4: Add mapper**

`StoreFieldMapper.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.starbucks

import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

object StoreFieldMapper {
  fun toEntity(
    fields: JsonObject,
    rawJson: String,
    now: Instant = Instant.now(),
  ): StoreEntity? {
    val id = fields.firstString("store_id") ?: return null
    val name = fields.firstString("name") ?: return null
    val (latitude, longitude) = fields.firstString("location")?.toLatLng() ?: return null
    val prefecture = fields.firstString("address_1").orEmpty()
    val fullAddress = listOfNotNull(
      fields.firstString("address_1"),
      fields.firstString("address_2"),
      fields.firstString("address_3"),
      fields.firstString("address_4"),
      fields.firstString("address_5"),
    ).joinToString("")

    return StoreEntity(
      id = id,
      name = name,
      nameEn = fields.firstString("en_name"),
      prefCode = fields.firstString("pref_code").orEmpty(),
      prefecture = prefecture,
      fullAddress = fullAddress,
      latitude = latitude,
      longitude = longitude,
      isReserve = fields.firstString("reserve_flg") == "1",
      rawJson = rawJson,
      lastSeenAt = now,
    )
  }

  private fun JsonObject.firstString(name: String): String? {
    val value = this[name] ?: return null
    return when (value) {
      is JsonPrimitive -> value.content
      else -> value.jsonArray.firstOrNull()?.let(JsonElement::toString)?.trim('"')
    }
  }

  private fun String.toLatLng(): Pair<Double, Double>? {
    val parts = split(",")
    if (parts.size != 2) {
      return null
    }
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return lat to lng
  }
}
```

- [ ] **Step 5: Add client**

`StarbucksStoreClient.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.starbucks

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import javax.inject.Inject
import kotlinx.coroutines.delay

class StarbucksStoreClient @Inject constructor(
  private val httpClient: HttpClient,
) {
  suspend fun fetchAllStores(): List<CloudSearchResponse.Hit> {
    val hits = mutableListOf<CloudSearchResponse.Hit>()
    var start = 0
    var found: Int

    do {
      val response = fetchPage(start)
      found = response.hits.found
      hits += response.hits.hit
      start += PageSize
      if (start < found) {
        delay(RequestIntervalMillis)
      }
    } while (start < found)

    return hits
  }

  private suspend fun fetchPage(start: Int): CloudSearchResponse {
    return httpClient.get(Endpoint) {
      header("Referer", "https://store.starbucks.co.jp/")
      header("User-Agent", "OneMoreCoffee/0.1.0 (Android; personal use)")
      parameter("size", PageSize)
      parameter("q.parser", "structured")
      parameter("q", "(and ver:10000 record_type:1)")
      parameter("fq", "(and data_type:'prd')")
      parameter("sort", "zip_code asc,store_id asc")
      parameter("start", start)
    }.body()
  }

  private companion object {
    const val Endpoint = "https://hn8madehag.execute-api.ap-northeast-1.amazonaws.com/prd-2019-08-21/storesearch"
    const val PageSize = 100
    const val RequestIntervalMillis = 1_500L
  }
}
```

- [ ] **Step 6: Run parser tests**

Run:

```bash
mise exec -- ./gradlew :core:data:test --tests '*StoreFieldMapperTest'
```

Expected: pass.

- [ ] **Step 7: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 8: Commit**

```bash
git add core/data
git commit -m "feat: スターバックス店舗マスタ取得処理を追加"
```

---

### Task 6: Visit Import Parser

**Files:**
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/importer/StarbucksVisitImportParser.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/importer/StarbucksVisitDto.kt`
- Test: `core/data/src/test/java/blue/starry/onemorecoffee/core/data/importer/StarbucksVisitImportParserTest.kt`

- [ ] **Step 1: Write parser tests**

Create `StarbucksVisitImportParserTest.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.importer

import blue.starry.onemorecoffee.core.domain.model.VisitSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class StarbucksVisitImportParserTest {
  @Test
  fun parse_deduplicatesFirstAndLastVisitOnSameDay() {
    val result = StarbucksVisitImportParser.parse(
      """
      [
        {
          "store_id": "1369",
          "last_visit_date": "2026-04-28 10:01:09",
          "first_visit_date": "2026-04-28 09:00:00",
          "frequency_of_visits": "3",
          "pref_code": 13,
          "name": "渋谷2丁目店",
          "is_exist": 1
        }
      ]
      """.trimIndent(),
    )

    assertThat(result.visits).hasSize(1)
    assertThat(result.visits.single().storeId).isEqualTo("1369")
    assertThat(result.visits.single().visitedOn).isEqualTo(LocalDate.of(2026, 4, 28))
    assertThat(result.visits.single().source).isEqualTo(VisitSource.IMPORTED_STARBUCKS)
  }

  @Test
  fun parse_keepsFirstAndLastVisitWhenDifferentDays() {
    val result = StarbucksVisitImportParser.parse(
      """
      [
        {
          "store_id": "1369",
          "last_visit_date": "2026-05-01 10:01:09",
          "first_visit_date": "2026-04-28 09:00:00",
          "frequency_of_visits": "2",
          "pref_code": 13,
          "name": "渋谷2丁目店",
          "is_exist": 1
        }
      ]
      """.trimIndent(),
    )

    assertThat(result.visits.map { it.visitedOn }).containsExactly(
      LocalDate.of(2026, 4, 28),
      LocalDate.of(2026, 5, 1),
    )
  }
}
```

- [ ] **Step 2: Run failing test**

Run:

```bash
mise exec -- ./gradlew :core:data:test --tests '*StarbucksVisitImportParserTest'
```

Expected: fail because parser does not exist.

- [ ] **Step 3: Add DTO and parser**

`StarbucksVisitDto.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.importer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StarbucksVisitDto(
  @SerialName("store_id")
  val storeId: String,
  @SerialName("last_visit_date")
  val lastVisitDate: String? = null,
  @SerialName("first_visit_date")
  val firstVisitDate: String? = null,
  @SerialName("frequency_of_visits")
  val frequencyOfVisits: String? = null,
  @SerialName("pref_code")
  val prefCode: Int? = null,
  val name: String? = null,
  @SerialName("is_exist")
  val isExist: Int? = null,
)
```

`StarbucksVisitImportParser.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.importer

import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json

object StarbucksVisitImportParser {
  private val json = Json {
    ignoreUnknownKeys = true
  }

  fun parse(rawJson: String): StarbucksVisitImportParseResult {
    val dtos = json.decodeFromString<List<StarbucksVisitDto>>(rawJson)
    val visits = dtos.flatMap { dto ->
      listOfNotNull(
        dto.firstVisitDate.toVisitDate(),
        dto.lastVisitDate.toVisitDate(),
      ).distinct().map { date ->
        VisitEntity(
          storeId = dto.storeId,
          visitedOn = date,
          source = VisitSource.IMPORTED_STARBUCKS,
        )
      }
    }

    return StarbucksVisitImportParseResult(
      visits = visits,
      rawCount = dtos.size,
    )
  }

  private fun String?.toVisitDate(): LocalDate? {
    if (this.isNullOrBlank()) {
      return null
    }
    return LocalDate.parse(take(10), DateTimeFormatter.ISO_LOCAL_DATE)
  }
}

data class StarbucksVisitImportParseResult(
  val visits: List<VisitEntity>,
  val rawCount: Int,
)
```

- [ ] **Step 4: Run parser tests**

Run:

```bash
mise exec -- ./gradlew :core:data:test --tests '*StarbucksVisitImportParserTest'
```

Expected: pass.

- [ ] **Step 5: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add core/data
git commit -m "feat: 訪問履歴インポートのパーサーを追加"
```

---

### Task 7: Repository Implementations and DI

**Files:**
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/di/DataModule.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/repository/StoreRepositoryImpl.kt`
- Create: `core/data/src/main/java/blue/starry/onemorecoffee/core/data/repository/VisitRepositoryImpl.kt`
- Modify: DAOs as needed for joins.
- Test: `core/data/src/test/java/blue/starry/onemorecoffee/core/data/repository/VisitRepositoryImplTest.kt`

- [ ] **Step 1: Add DAO query for current store summaries**

Extend `StoreDao.kt`:

```kotlin
@Query(
  """
  SELECT
    stores.id AS id,
    stores.name AS name,
    stores.prefecture AS prefecture,
    stores.fullAddress AS fullAddress,
    stores.latitude AS latitude,
    stores.longitude AS longitude,
    stores.isReserve AS isReserve,
    COUNT(visits.id) AS visitCount,
    MAX(visits.visitedOn) AS lastVisitedOn
  FROM stores
  LEFT JOIN visits ON stores.id = visits.storeId
  GROUP BY stores.id
  ORDER BY stores.prefCode, stores.name
  """,
)
fun observeSummaries(): Flow<List<StoreVisitSummary>>
```

- [ ] **Step 2: Add Store repository implementation**

`StoreRepositoryImpl.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.repository

import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.starbucks.StarbucksStoreClient
import blue.starry.onemorecoffee.core.data.starbucks.StoreFieldMapper
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class StoreRepositoryImpl @Inject constructor(
  private val storeDao: StoreDao,
  private val starbucksStoreClient: StarbucksStoreClient,
) : StoreRepository {
  override fun observeStoreSummaries(): Flow<List<StoreVisitSummary>> {
    return storeDao.observeSummaries()
  }

  override suspend fun refreshStores(): StoreRefreshResult {
    val hits = starbucksStoreClient.fetchAllStores()
    val stores = hits.mapNotNull { hit ->
      StoreFieldMapper.toEntity(hit.fields, rawJson = hit.fields.toString())
    }
    storeDao.upsertAll(stores)
    storeDao.deleteStoresNotIn(stores.map { it.id })

    return StoreRefreshResult(
      upserted = stores.size,
      skipped = hits.size - stores.size,
    )
  }
}
```

- [ ] **Step 3: Add Visit repository implementation**

`VisitRepositoryImpl.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.repository

import android.webkit.CookieManager
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.importer.StarbucksVisitImportParser
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import javax.inject.Inject

class VisitRepositoryImpl @Inject constructor(
  private val visitDao: VisitDao,
  private val storeDao: StoreDao,
) : VisitRepository {
  override suspend fun importStarbucksVisits(json: String): VisitImportResult {
    val parsed = StarbucksVisitImportParser.parse(json)
    val knownStoreIds = storeDao.ids().toSet()
    val insertedResults = visitDao.insertIgnore(parsed.visits)
    val inserted = insertedResults.count { it != -1L }

    return VisitImportResult(
      inserted = inserted,
      duplicated = insertedResults.size - inserted,
      unknownStoreVisits = parsed.visits.count { it.storeId !in knownStoreIds },
      failed = 0,
    )
  }

  override suspend fun logoutImporter() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
  }
}
```

- [ ] **Step 4: Add DI module**

`DataModule.kt`:

```kotlin
package blue.starry.onemorecoffee.core.data.di

import android.content.Context
import androidx.room.Room
import blue.starry.onemorecoffee.core.data.database.OneMoreCoffeeDatabase
import blue.starry.onemorecoffee.core.data.repository.StoreRepositoryImpl
import blue.starry.onemorecoffee.core.data.repository.VisitRepositoryImpl
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds
  abstract fun bindStoreRepository(impl: StoreRepositoryImpl): StoreRepository

  @Binds
  abstract fun bindVisitRepository(impl: VisitRepositoryImpl): VisitRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): OneMoreCoffeeDatabase {
    return Room.databaseBuilder(
      context,
      OneMoreCoffeeDatabase::class.java,
      "one_more_coffee.db",
    ).build()
  }

  @Provides
  fun provideStoreDao(database: OneMoreCoffeeDatabase) = database.storeDao()

  @Provides
  fun provideVisitDao(database: OneMoreCoffeeDatabase) = database.visitDao()

  @Provides
  @Singleton
  fun provideHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
      install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
      }
    }
  }
}
```

- [ ] **Step 5: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add core/data
git commit -m "feat: 店舗と訪問履歴のリポジトリを追加"
```

---

### Task 8: App Navigation and Feature Screen Shells

**Files:**
- Modify: `app/src/main/java/blue/starry/onemorecoffee/App.kt`
- Create: `app/src/main/java/blue/starry/onemorecoffee/Route.kt`
- Create: `feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreen.kt`
- Create: `feature/list/src/main/java/blue/starry/onemorecoffee/feature/list/StoreListScreen.kt`
- Create: `feature/stats/src/main/java/blue/starry/onemorecoffee/feature/stats/StatsScreen.kt`
- Create: `feature/settings/src/main/java/blue/starry/onemorecoffee/feature/settings/SettingsScreen.kt`
- Create: `feature/import/src/main/java/blue/starry/onemorecoffee/feature/import/ImportScreen.kt`

- [ ] **Step 1: Add route model**

`Route.kt`:

```kotlin
package blue.starry.onemorecoffee

enum class Route(
  val label: String,
) {
  Map("マップ"),
  List("リスト"),
  Stats("統計"),
  Settings("設定"),
}
```

- [ ] **Step 2: Add feature shell composables**

Example `MapScreen.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.map

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun MapScreen() {
  Text("マップ")
}
```

Repeat for List/Stats/Settings/Import with matching display text.

- [ ] **Step 3: Add bottom navigation shell**

`App.kt`:

```kotlin
package blue.starry.onemorecoffee

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import blue.starry.onemorecoffee.feature.list.StoreListScreen
import blue.starry.onemorecoffee.feature.map.MapScreen
import blue.starry.onemorecoffee.feature.settings.SettingsScreen
import blue.starry.onemorecoffee.feature.stats.StatsScreen

@Composable
fun App() {
  var currentRoute by remember { mutableStateOf(Route.Map) }

  Scaffold(
    bottomBar = {
      NavigationBar {
        Route.entries.forEach { route ->
          NavigationBarItem(
            selected = currentRoute == route,
            onClick = { currentRoute = route },
            label = { Text(route.label) },
            icon = { Text(route.label.first().toString()) },
          )
        }
      }
    },
  ) { paddingValues ->
    when (currentRoute) {
      Route.Map -> MapScreen()
      Route.List -> StoreListScreen(Modifier.padding(paddingValues))
      Route.Stats -> StatsScreen(Modifier.padding(paddingValues))
      Route.Settings -> SettingsScreen(Modifier.padding(paddingValues))
    }
  }
}
```

- [ ] **Step 4: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add app feature
git commit -m "feat: MVP画面のナビゲーションを追加"
```

---

### Task 9: Map Screen with Google Maps Compose

**Files:**
- Modify: `feature/map/build.gradle.kts`
- Modify: `feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreen.kt`
- Create: `feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreenViewModel.kt`

- [ ] **Step 1: Add map dependencies**

In `feature/map/build.gradle.kts` add:

```kotlin
implementation(libs.android.maps.compose)
implementation(libs.android.maps.compose.utils)
implementation(libs.play.services.maps)
implementation(libs.play.services.location)
implementation(libs.androidx.hilt.navigation.compose)
```

- [ ] **Step 2: Add ViewModel**

`MapScreenViewModel.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MapScreenViewModel @Inject constructor(
  observeStoreSummariesUseCase: ObserveStoreSummariesUseCase,
) : ViewModel() {
  val uiState: StateFlow<MapUiState> = observeStoreSummariesUseCase()
    .map { summaries ->
      if (summaries.isEmpty()) {
        MapUiState.Empty
      } else {
        MapUiState.Ready(summaries)
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState.Loading)
}

sealed interface MapUiState {
  data object Loading : MapUiState
  data object Empty : MapUiState
  data class Ready(val stores: List<StoreVisitSummary>) : MapUiState
}
```

- [ ] **Step 3: Implement map screen**

`MapScreen.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(
  modifier: Modifier = Modifier,
  viewModel: MapScreenViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()

  when (val state = uiState) {
    MapUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    MapUiState.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("店舗データを更新してください")
    }
    is MapUiState.Ready -> {
      val tokyo = LatLng(35.681236, 139.767125)
      val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(tokyo, 11f)
      }
      GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
      ) {
        state.stores.forEach { store ->
          Marker(
            state = MarkerState(LatLng(store.latitude, store.longitude)),
            title = store.name,
            snippet = if (store.isVisited) "Visited" else "Unvisited",
          )
        }
      }
    }
  }
}
```

- [ ] **Step 4: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add feature/map
git commit -m "feat: 店舗マップ画面を追加"
```

---

### Task 10: List, Stats, and Settings Screens

**Files:**
- Create/Modify ViewModels and screens in `feature/list`, `feature/stats`, `feature/settings`

- [ ] **Step 1: Add list ViewModel**

Create `feature/list/src/main/java/blue/starry/onemorecoffee/feature/list/StoreListScreenViewModel.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StoreListScreenViewModel @Inject constructor(
  observeStoreSummariesUseCase: ObserveStoreSummariesUseCase,
) : ViewModel() {
  private val query = MutableStateFlow("")
  private val visitedFilter = MutableStateFlow<VisitedFilter>(VisitedFilter.All)

  val uiState: StateFlow<StoreListUiState> = combine(
    observeStoreSummariesUseCase(),
    query,
    visitedFilter,
  ) { stores, q, filter ->
    StoreListUiState(
      query = q,
      visitedFilter = filter,
      stores = stores.filter { store ->
        val matchesQuery = q.isBlank() || store.name.contains(q, ignoreCase = true) || store.fullAddress.contains(q, ignoreCase = true)
        val matchesFilter = when (filter) {
          VisitedFilter.All -> true
          VisitedFilter.Visited -> store.isVisited
          VisitedFilter.Unvisited -> !store.isVisited
        }
        matchesQuery && matchesFilter
      },
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreListUiState())

  fun updateQuery(value: String) {
    query.value = value
  }

  fun updateVisitedFilter(value: VisitedFilter) {
    visitedFilter.value = value
  }
}

data class StoreListUiState(
  val query: String = "",
  val visitedFilter: VisitedFilter = VisitedFilter.All,
  val stores: List<StoreVisitSummary> = emptyList(),
)

enum class VisitedFilter {
  All,
  Visited,
  Unvisited,
}
```

- [ ] **Step 2: Implement list screen**

Use `OutlinedTextField`, `FilterChip`, and `LazyColumn` to show `state.stores`. Each row must show name, address, and either `最終訪問: yyyy-MM-dd` or `未訪問`.

- [ ] **Step 3: Add stats ViewModel and screen**

`StatsScreenViewModel` should expose `ObserveProgressStatsUseCase()` as StateFlow. `StatsScreen` should show three cards: current stores, visited stores, completion percentage formatted as one decimal place.

- [ ] **Step 4: Add settings ViewModel and screen**

`SettingsScreenViewModel` should inject `StoreRepository` and `VisitRepository` and expose:

```kotlin
fun refreshStores()
fun logoutImporter()
```

The screen should show buttons for 店舗データ更新, 訪問履歴インポート, ログアウト, and a short warning that Maps API key must be Android/API restricted.

- [ ] **Step 5: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add feature/list feature/stats feature/settings
git commit -m "feat: リスト統計設定画面を追加"
```

---

### Task 11: WebView Import Screen

**Files:**
- Modify: `feature/import/build.gradle.kts`
- Create: `feature/import/src/main/java/blue/starry/onemorecoffee/feature/import/ImportScreenViewModel.kt`
- Modify: `feature/import/src/main/java/blue/starry/onemorecoffee/feature/import/ImportScreen.kt`

- [ ] **Step 1: Add ViewModel**

`ImportScreenViewModel.kt`:

```kotlin
package blue.starry.onemorecoffee.feature.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ImportScreenViewModel @Inject constructor(
  private val visitRepository: VisitRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<ImportUiState>(ImportUiState.Waiting)
  val uiState: StateFlow<ImportUiState> = mutableUiState

  fun importJson(json: String) {
    viewModelScope.launch {
      mutableUiState.value = ImportUiState.Importing
      runCatching {
        visitRepository.importStarbucksVisits(json)
      }.onSuccess {
        mutableUiState.value = ImportUiState.Completed(it)
      }.onFailure {
        mutableUiState.value = ImportUiState.Failed("Failed to import visits.")
      }
    }
  }
}

sealed interface ImportUiState {
  data object Waiting : ImportUiState
  data object Importing : ImportUiState
  data class Completed(val result: VisitImportResult) : ImportUiState
  data class Failed(val message: String) : ImportUiState
}
```

- [ ] **Step 2: Add JavaScript bridge**

Inside `ImportScreen.kt`, define:

```kotlin
class StarbucksImportBridge(
  private val onJsonReceived: (String) -> Unit,
) {
  @android.webkit.JavascriptInterface
  fun receiveStoreAll(json: String) {
    onJsonReceived(json)
  }
}
```

- [ ] **Step 3: Implement WebView**

Use `AndroidView` with a `WebView` configured as:

```kotlin
settings.javaScriptEnabled = true
settings.domStorageEnabled = true
settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
addJavascriptInterface(StarbucksImportBridge(viewModel::importJson), "OneMoreCoffee")
webViewClient = object : WebViewClient() {
  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    return request.url.host?.endsWith("starbucks.co.jp") != true
  }

  override fun onPageFinished(view: WebView, url: String) {
    if (url.contains("/mystarbucks/mystore/")) {
      view.evaluateJavascript(
        """
        (function() {
          if (window.Stamp && Array.isArray(window.Stamp.store_all)) {
            window.OneMoreCoffee.receiveStoreAll(JSON.stringify(window.Stamp.store_all));
          }
        })();
        """.trimIndent(),
        null,
      )
    }
  }
}
loadUrl("https://www.starbucks.co.jp/mystarbucks/mystore/")
```

- [ ] **Step 4: Show import status**

Above or below the WebView, show:

- Waiting: `My Starbucks にログインしてください`
- Importing: progress indicator
- Completed: `追加: X / 重複: Y / マスタ外: Z`
- Failed: English error message from state

- [ ] **Step 5: Wire settings to import screen**

If the simple `App` state navigation is still used, add an app-level state that opens `ImportScreen` when 訪問履歴インポート is tapped. Keep the bottom navigation hidden or replaced by a top-level back button while importing.

- [ ] **Step 6: Run verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add app feature/import feature/settings
git commit -m "feat: My Starbucks訪問履歴インポート画面を追加"
```

---

### Task 12: Polish Store Details, Directions, and Final Verification

**Files:**
- Modify: `feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreen.kt`
- Modify: `feature/list/src/main/java/blue/starry/onemorecoffee/feature/list/StoreListScreen.kt`
- Create: `core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/StoreDetailSheet.kt`

- [ ] **Step 1: Add shared store detail component**

`StoreDetailSheet.kt`:

```kotlin
package blue.starry.onemorecoffee.core.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary

@Composable
fun StoreDetailSheet(
  store: StoreVisitSummary,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
  ) {
    Text(store.name, style = MaterialTheme.typography.titleLarge)
    Text(store.fullAddress, style = MaterialTheme.typography.bodyMedium)
    Text(
      if (store.isVisited) {
        "訪問回数: ${store.visitCount} / 最終訪問: ${store.lastVisitedOn}"
      } else {
        "未訪問"
      },
      style = MaterialTheme.typography.bodyMedium,
    )
    Button(
      onClick = {
        val uri = Uri.parse("google.navigation:q=${store.latitude},${store.longitude}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
      },
    ) {
      Text("経路検索")
    }
  }
}
```

- [ ] **Step 2: Use detail component from map and list**

In map, set selected store on marker click and show `ModalBottomSheet` with `StoreDetailSheet`. In list, open the same sheet when a row is tapped.

- [ ] **Step 3: Add README**

Create `README.md` with:

```markdown
# OneMoreCoffee

OneMoreCoffee is a personal Android app for tracking Starbucks My Store Passport progress.

## Google Maps API key

Create `secrets.properties` locally:

```properties
MAPS_API_KEY=your_api_key
```

Restrict the key in Google Cloud:

- Android app restriction with package name `blue.starry.onemorecoffee`
- SHA-1 certificate fingerprint for the signing certificate
- API restriction to Maps SDK for Android

The MVP does not use Places SDK or other Google Maps Platform APIs that support Firebase App Check.
```

- [ ] **Step 4: Full verification**

Run:

```bash
mise exec -- ./gradlew test lint assembleDebug
```

Expected: pass.

- [ ] **Step 5: Inspect git status**

Run:

```bash
git status --short
```

Expected: only intentional files are modified or untracked. `secrets.properties`, `design.md`, and ignored docs should not be staged.

- [ ] **Step 6: Final commit**

```bash
git add README.md app core feature gradle libs.versions.toml settings.gradle.kts build.gradle.kts
git commit -m "feat: OneMoreCoffeeのMVPを実装"
```

---

## Self-Review

- Spec coverage:
  - Store master fetch: Task 5 and Task 7.
  - Room storage: Task 4 and Task 7.
  - WebView visit import: Task 6 and Task 11.
  - Day-level visits and `storeId + visitedOn` uniqueness: Task 4 and Task 6.
  - No Store FK for Visit: Task 4.
  - Current-store-only `Store` with non-null coordinates: Task 4 and Task 5.
  - Map/List/Stats/Settings/Import UI: Tasks 8 through 12.
  - Google Maps API key restrictions: Task 1 and Task 12 README.
  - No manual visits, pre-open stores, Places SDK, or distribution: not implemented in any task.
- Placeholder scan:
  - No `TBD`, `TODO`, or undefined future-only task remains.
- Type consistency:
  - Domain names use `StoreVisitSummary`, `ProgressStats`, `VisitSource.IMPORTED_STARBUCKS`.
  - Database names use `StoreEntity`, `VisitEntity`, `StoreDao`, `VisitDao`, `OneMoreCoffeeDatabase`.
  - Import result names match `VisitImportResult`.
