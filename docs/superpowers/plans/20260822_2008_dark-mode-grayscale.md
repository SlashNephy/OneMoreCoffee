# ダークモード対応・グレースケール視認性 実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** システムのダークモードに追従させ、マップの視覚表現から色相への依存を取り除いて、グレースケール表示でも訪問状態が判別できるようにする。

**Architecture:** `OneMoreCoffeeTheme` に `darkColorScheme` を追加して `isSystemInDarkTheme()` で切り替える。地図タイルは `ComposeMapColorScheme.FOLLOW_SYSTEM` に任せる。マーカーは「塗り / 中空」と「Reserve バッジの有無」という直交する 2 軸で表現し、スタイル決定を純関数に切り出してテストする。ビットマップは白の外周リングによりテーマ非依存に保ち、アイコンキャッシュのキーにテーマ次元を持ち込まない。

**Tech Stack:** Kotlin, Jetpack Compose (Material3), maps-compose 8.4.0, JUnit 4 + Truth

設計書: [2026-08-22-dark-mode-grayscale-design.md](../specs/2026-08-22-dark-mode-grayscale-design.md)

## Global Constraints

- 対象ブランチは `feat/dark-mode-grayscale`。ベースは `main`。
- 公開リポジトリである。成果物にローカル固有のパスや非公開情報を含めない。
- コミットメッセージは Conventional Commits 形式。末尾に `Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>` を付ける。
- コード内コメントは日本語。ログ・エラーメッセージは英語。
- スターバックス公式グリーン `#00704A` は使用しない。基調色は `#006241`。
- ソースコードを走査してパターンの有無を検査するテストや、定数をそのコピーと比較するだけのテストは書かない。
- `minSdk = 26` / `compileSdk = 37` は変更しない。
- テストの実行コマンドは `./gradlew :feature:map:testDebugUnitTest` のようにモジュール単位で指定する。全体検証は `./gradlew test` とする。

## File Structure

| ファイル | 責務 |
|---|---|
| `core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/OneMoreCoffeeTheme.kt` | ライト / ダーク両方のカラースキーム定義と切替 |
| `app/src/main/res/values/colors.xml`（新規） | ライト時のウィンドウ背景色 |
| `app/src/main/res/values-night/colors.xml`（新規） | ダーク時のウィンドウ背景色 |
| `app/src/main/res/values/themes.xml` | `AppTheme`（ライト）。`windowBackground` を明示 |
| `app/src/main/res/values-night/themes.xml`（新規） | `AppTheme`（ダーク） |
| `feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreen.kt` | マーカースタイルの決定ロジックと描画、地図タイルの色スキーム指定 |
| `feature/map/src/main/res/drawable/star_fill.xml`（新規） | Reserve バッジの星形 |
| `feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapMarkerStyleTest.kt`（新規） | `markerStyleFor` のテスト |
| `feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapClusterLabelTest.kt` | `clusterStyleFor` への追随 |
| `docs/design.md` | 7.3 節「ピンの視覚言語」の更新 |

**設計書からの逸脱を 1 点記録する。** 設計書 6 節では `clusterStyleFor` の返り値を `ClusterStyle` としていたが、実装ではピンと共通の `MarkerFill` enum を返す。「塗り / 中空」という同一の語彙を 2 つの型で二重に定義する必要がないためである。

---

### Task 1: ダークカラースキーム

**Files:**
- Modify: `core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/OneMoreCoffeeTheme.kt`

**Interfaces:**
- Consumes: なし
- Produces: `OneMoreCoffeeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`。既存の呼び出し側（`MainActivity`）は引数なしのまま動作する。

このタスクにユニットテストは付けない。カラースキームの各スロットに定数を代入するだけの実装であり、それを検証するテストは定数をコピーして比較するだけのものになる。描画結果は Task 4 の実機検証で確認する。

- [x] **Step 1: カラースキームを書き換える**

`OneMoreCoffeeTheme.kt` の全体を次で置き換える。

```kotlin
package blue.starry.onemorecoffee.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006241),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FF8C4),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFFC98A3B),
    onSecondary = Color(0xFF3B2708),
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2A1800),
    tertiary = Color(0xFF2E5C8A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFFEFEEE9),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6ADBA8),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFF86F8C3),
    secondary = Color(0xFFE7BE7E),
    onSecondary = Color(0xFF432C05),
    secondaryContainer = Color(0xFF5E421A),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF9FC9FF),
    onTertiary = Color(0xFF003257),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DD),
    surfaceContainer = Color(0xFF1C201B),
    surfaceVariant = Color(0xFF3F4A42),
    onSurfaceVariant = Color(0xFFBFC9BE),
    outline = Color(0xFF89938B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun OneMoreCoffeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
```

- [x] **Step 2: コンパイルを確認する**

Run: `./gradlew :core:ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

`androidx.compose.foundation.isSystemInDarkTheme` が解決できない場合は、`material3` 経由の推移的な公開が効いていない。その場合のみ `gradle/libs.versions.toml` の `[libraries]` に次を追加し、`core/ui/build.gradle.kts` の `dependencies` に `implementation(libs.androidx.compose.foundation)` を加えてから再実行する。

```toml
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
```

- [x] **Step 3: アプリ全体のコンパイルを確認する**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（`MainActivity` の `OneMoreCoffeeTheme { App() }` は既定引数によりそのまま通る）

- [x] **Step 4: コミット**

```bash
git add core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/OneMoreCoffeeTheme.kt
git commit -m "$(cat <<'EOF'
feat(ui): ダークカラースキームを追加してシステム設定に追従する

ライト側も派生スロットを明示し、Material3 の既定である紫系の
フォールバックが混ざらないようにする。

Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 起動時ウィンドウ背景のダーク対応

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values-night/colors.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: Task 1 で定めた `background` の値（ライト `#F8F7F4` / ダーク `#101410`）
- Produces: `@color/window_background`

これを入れないと、ダークモードでの起動時に Compose が最初のフレームを描くまでの間だけ白いウィンドウが表示される。リソースの選択は Android のリソース解決に委ねられるためユニットテストの対象にしない。Task 4 の実機検証で、起動直後にフラッシュが出ないことを確認する。

- [x] **Step 1: ライト用の色リソースを作成する**

`app/src/main/res/values/colors.xml`

```xml
<resources>
    <color name="window_background">#FFF8F7F4</color>
</resources>
```

- [x] **Step 2: ダーク用の色リソースを作成する**

`app/src/main/res/values-night/colors.xml`

```xml
<resources>
    <color name="window_background">#FF101410</color>
</resources>
```

- [x] **Step 3: ライトのテーマに windowBackground を追加する**

`app/src/main/res/values/themes.xml` の全体を次で置き換える。

```xml
<resources>
    <style name="AppTheme" parent="@android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
    </style>
</resources>
```

- [x] **Step 4: ダークのテーマを作成する**

`app/src/main/res/values-night/themes.xml`

```xml
<resources>
    <style name="AppTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
    </style>
</resources>
```

- [x] **Step 5: リソースがビルドを通ることを確認する**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [x] **Step 6: コミット**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml
git commit -m "$(cat <<'EOF'
feat(app): ダークモード時のウィンドウ背景を定義する

起動時に Compose が最初のフレームを描くまでの白いフラッシュを防ぐ。

Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: マーカーの視覚言語

**Files:**
- Create: `feature/map/src/main/res/drawable/star_fill.xml`
- Create: `feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapMarkerStyleTest.kt`
- Modify: `feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreen.kt`
- Modify: `feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapClusterLabelTest.kt`
- Modify: `docs/design.md:346-354`

**Interfaces:**
- Consumes: なし
- Produces:
  - `internal enum class MarkerFill { Filled, Hollow }`
  - `internal data class StoreMarkerStyle(val fill: MarkerFill, val hasReserveBadge: Boolean)`
  - `internal fun markerStyleFor(isVisited: Boolean, isReserve: Boolean): StoreMarkerStyle`
  - `internal fun clusterStyleFor(totalCount: Int, visitedCount: Int): MarkerFill`

スタイル決定ロジックと描画を 1 つのタスクにまとめる。分割すると、スタイルを計算しているのに描画へ反映していない中間状態がコミットとして残るためである。テスト先行の順序（失敗するテスト → 純関数 → 描画）は保つ。

ビットマップ生成そのものはユニットテストに向かないため、テストの対象は純関数に限る。描画結果は Task 4 の実機検証で確認する。

現状のコードは `store.isVisited -> Visited` / `store.isReserve -> Reserve` という排他的な `when` になっており、訪問済の Reserve 店舗が Reserve であることを失う。`markerStyleFor` の 4 通りのテストはこの欠陥に対する回帰テストである。

- [x] **Step 1: 失敗するテストを書く**

`feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapMarkerStyleTest.kt` を新規作成する。

```kotlin
package blue.starry.onemorecoffee.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapMarkerStyleTest {
    @Test
    fun markerStyleFor_treatsVisitedAndReserveAsIndependentAxes() {
        assertThat(markerStyleFor(isVisited = false, isReserve = false))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Hollow, hasReserveBadge = false))
        assertThat(markerStyleFor(isVisited = true, isReserve = false))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Filled, hasReserveBadge = false))
        assertThat(markerStyleFor(isVisited = false, isReserve = true))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Hollow, hasReserveBadge = true))
    }

    @Test
    fun markerStyleFor_keepsReserveBadgeOnVisitedStore() {
        assertThat(markerStyleFor(isVisited = true, isReserve = true))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Filled, hasReserveBadge = true))
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./gradlew :feature:map:testDebugUnitTest --tests '*MapMarkerStyleTest'`
Expected: コンパイルエラー。`Unresolved reference: markerStyleFor` および `Unresolved reference: MarkerFill`

- [x] **Step 3: 既存テストを新しい API に合わせて書き換える**

`MapClusterLabelTest.kt` の `clusterFillColor_usesRedWhenUnvisitedStoresAreMajority` を次で置き換える。他のテストメソッドは変更しない。

```kotlin
    @Test
    fun clusterStyleFor_isHollowWhenUnvisitedStoresAreMajority() {
        assertThat(clusterStyleFor(totalCount = 4, visitedCount = 2)).isEqualTo(MarkerFill.Filled)
        assertThat(clusterStyleFor(totalCount = 5, visitedCount = 2)).isEqualTo(MarkerFill.Hollow)
    }
```

- [x] **Step 4: 純関数を実装する**

`MapScreen.kt` の既存の `StoreMarkerStyle` enum 定義を削除する。

```kotlin
private enum class StoreMarkerStyle(
    val color: Int,
) {
    Visited(0xFF2E7D32.toInt()),
    Reserve(0xFF6A1B9A.toInt()),
    Unvisited(0xFFC62828.toInt()),
}
```

削除した位置に次を追加する。

```kotlin
/** マーカーの塗り方。訪問済は塗りつぶし、未訪問は中空で表す。 */
internal enum class MarkerFill {
    Filled,
    Hollow,
}

/**
 * 店舗マーカーの見た目。
 *
 * 訪問状態（塗り / 中空）と Reserve（バッジの有無）は直交する 2 軸として扱う。
 */
internal data class StoreMarkerStyle(
    val fill: MarkerFill,
    val hasReserveBadge: Boolean,
)

internal fun markerStyleFor(
    isVisited: Boolean,
    isReserve: Boolean,
): StoreMarkerStyle {
    return StoreMarkerStyle(
        fill = if (isVisited) MarkerFill.Filled else MarkerFill.Hollow,
        hasReserveBadge = isReserve,
    )
}
```

続いて既存の `clusterFillColor` を次で置き換える。

```kotlin
internal fun clusterFillColor(
    totalCount: Int,
    visitedCount: Int,
): Int {
    val unvisitedCount = totalCount - visitedCount
    return if (unvisitedCount > totalCount / 2) {
        ClusterUnvisitedMajorityColor
    } else {
        ClusterDefaultColor
    }
}
```

置き換え後。

```kotlin
internal fun clusterStyleFor(
    totalCount: Int,
    visitedCount: Int,
): MarkerFill {
    val unvisitedCount = totalCount - visitedCount
    return if (unvisitedCount > totalCount / 2) {
        MarkerFill.Hollow
    } else {
        MarkerFill.Filled
    }
}
```

- [x] **Step 5: 色定数を整理する**

`MapScreen.kt` 末尾の色定数を次で置き換える。

```kotlin
private const val ClusterReleaseZoom = 14f
private const val ClusterDefaultColor = 0xFF00704A.toInt()
private const val ClusterUnvisitedMajorityColor = 0xFFC62828.toInt()
```

置き換え後。

```kotlin
private const val ClusterReleaseZoom = 14f

/** 基調色。スターバックス公式グリーン `#00704A` は使用しない。 */
private const val MarkerBrandColor = 0xFF006241.toInt()

/** 明タイル・暗タイルのどちらでも輪郭が立つよう、全マーカーに付ける外周リング。 */
private const val MarkerRingColor = 0xFFFFFFFF.toInt()
private const val MarkerHollowFillColor = 0xFFFFFFFF.toInt()
private const val MarkerOnBrandColor = 0xFFFFFFFF.toInt()
private const val ReserveBadgeFillColor = 0xFFC98A3B.toInt()
private const val ReserveBadgeForegroundColor = 0xFF3B2708.toInt()
```

- [x] **Step 6: 星形の drawable を作成する**

`feature/map/src/main/res/drawable/star_fill.xml`

`local_cafe_fill.xml` と同じ 960 viewport に、中心 (480, 480)・外接半径 440・内接半径 210 の 5 芒星を配置する。Material Symbols の星ではなく手書きの単純な星形とするのは、バッジ内 9dp 相当で描かれるため、細部を持つ図案が潰れるからである。

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <path
        android:fillColor="#000000"
        android:pathData="M480,40 L603.44,310.11 L898.47,344.03 L679.72,544.89 L738.63,835.97 L480,690 L221.37,835.97 L280.28,544.89 L61.53,344.03 L356.56,310.11 Z" />
</vector>
```

- [x] **Step 7: 店舗マーカーの描画を書き換える**

`createStoreMarkerBitmap` の全体を次で置き換える。

```kotlin
private fun createStoreMarkerBitmap(
    context: Context,
    style: StoreMarkerStyle,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val center = size / 2f
    val ringWidth = 2f * density
    val strokeWidth = 3f * density
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    // 白の下地。塗りの場合はそのまま外周リングになり、中空の場合は内側の地色になる
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MarkerRingColor
    }
    canvas.drawCircle(center, center, center, ringPaint)

    val iconColor = when (style.fill) {
        MarkerFill.Filled -> {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarkerBrandColor
            }
            canvas.drawCircle(center, center, center - ringWidth, fillPaint)
            MarkerOnBrandColor
        }

        MarkerFill.Hollow -> {
            val hollowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarkerHollowFillColor
            }
            canvas.drawCircle(center, center, center - ringWidth, hollowPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarkerBrandColor
                this.strokeWidth = strokeWidth
                this.style = Paint.Style.STROKE
            }
            canvas.drawCircle(center, center, center - ringWidth - strokeWidth / 2f, strokePaint)
            MarkerBrandColor
        }
    }

    val icon = requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.local_cafe_fill, context.theme)) {
        "local_cafe_fill drawable is missing"
    }.mutate()
    icon.setTint(iconColor)

    val iconSize = (18 * density).toInt()
    val iconLeft = (size - iconSize) / 2
    val iconTop = (size - iconSize) / 2
    icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
    icon.draw(canvas)

    if (style.hasReserveBadge) {
        drawReserveBadge(context, canvas, size.toFloat(), density)
    }

    return bitmap
}

/** Reserve 店舗であることを右上のバッジで示す。訪問状態とは独立した軸として重ねる。 */
private fun drawReserveBadge(
    context: Context,
    canvas: Canvas,
    size: Float,
    density: Float,
) {
    val badgeRadius = 8f * density
    val badgeBorder = 2f * density
    val badgeCenterX = size - badgeRadius - density
    val badgeCenterY = badgeRadius + density

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MarkerRingColor
    }
    canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, borderPaint)

    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ReserveBadgeFillColor
    }
    canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius - badgeBorder, badgePaint)

    val star = requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.star_fill, context.theme)) {
        "star_fill drawable is missing"
    }.mutate()
    star.setTint(ReserveBadgeForegroundColor)

    val starSize = (9f * density).toInt()
    val starLeft = (badgeCenterX - starSize / 2f).toInt()
    val starTop = (badgeCenterY - starSize / 2f).toInt()
    star.setBounds(starLeft, starTop, starLeft + starSize, starTop + starSize)
    star.draw(canvas)
}
```

- [x] **Step 8: 店舗マーカーの呼び出し側を直す**

`storeIconFor` を次で置き換える。

```kotlin
    private fun storeIconFor(store: StoreVisitSummary): BitmapDescriptor {
        val style = markerStyleFor(isVisited = store.isVisited, isReserve = store.isReserve)

        return storeIconCache.getOrPut(style) {
            BitmapDescriptorFactory.fromBitmap(createStoreMarkerBitmap(context, style))
        }
    }
```

`storeIconCache` の型宣言は `mutableMapOf<StoreMarkerStyle, BitmapDescriptor>()` のままでよい。`StoreMarkerStyle` が data class になったため、キーとしての等価性は引き続き成立する。

- [x] **Step 9: クラスタの描画を書き換える**

`createClusterMarkerBitmap` の全体を次で置き換える。

```kotlin
private fun createClusterMarkerBitmap(
    context: Context,
    label: String,
    fill: MarkerFill,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val ringWidth = 2f * density
    val strokeWidth = 3f * density
    val labelColor = when (fill) {
        MarkerFill.Filled -> MarkerOnBrandColor
        MarkerFill.Hollow -> MarkerBrandColor
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textSize = 13 * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textBounds = Rect()
    textPaint.getTextBounds(label, 0, label.length, textBounds)

    val horizontalPadding = (10 * density).toInt()
    val verticalPadding = (7 * density).toInt()
    val height = maxOf((34 * density).toInt(), textBounds.height() + verticalPadding * 2)
    val width = maxOf(height, textBounds.width() + horizontalPadding * 2)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MarkerRingColor
    }
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, ringPaint)

    when (fill) {
        MarkerFill.Filled -> {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarkerBrandColor
            }
            canvas.drawRoundRect(
                ringWidth,
                ringWidth,
                width - ringWidth,
                height - ringWidth,
                (height - ringWidth * 2f) / 2f,
                (height - ringWidth * 2f) / 2f,
                fillPaint,
            )
        }

        MarkerFill.Hollow -> {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarkerBrandColor
                this.strokeWidth = strokeWidth
                style = Paint.Style.STROKE
            }
            val inset = ringWidth + strokeWidth / 2f
            canvas.drawRoundRect(
                inset,
                inset,
                width - inset,
                height - inset,
                (height - inset * 2f) / 2f,
                (height - inset * 2f) / 2f,
                strokePaint,
            )
        }
    }

    canvas.drawText(
        label,
        (width - textBounds.width()) / 2f - textBounds.left,
        (height + textBounds.height()) / 2f - textBounds.bottom,
        textPaint,
    )

    return bitmap
}
```

- [x] **Step 10: クラスタの呼び出し側を直す**

`ClusterIconKey` を次で置き換える。

```kotlin
private data class ClusterIconKey(
    val label: String,
    val fill: MarkerFill,
)
```

`clusterIconFor` を次で置き換える。

```kotlin
    private fun clusterIconFor(cluster: Cluster<StoreClusterItem>): BitmapDescriptor {
        val visitedCount = cluster.items.count { it.store.isVisited }
        val label = buildClusterLabel(
            totalCount = cluster.size,
            visitedCount = visitedCount,
        )
        val fill = clusterStyleFor(
            totalCount = cluster.size,
            visitedCount = visitedCount,
        )
        val cacheKey = ClusterIconKey(label = label, fill = fill)

        return clusterIconCache.getOrPut(cacheKey) {
            BitmapDescriptorFactory.fromBitmap(createClusterMarkerBitmap(context, label, fill))
        }
    }
```

- [x] **Step 11: 未使用インポートを整理する**

`android.graphics.Color` のインポートは `Color.WHITE` を使わなくなったため不要になる。`MapScreen.kt` の import から `import android.graphics.Color` を削除する。

- [x] **Step 12: 地図タイルをシステム追従にする**

`MapScreen.kt` の import に次を追加する。

```kotlin
import com.google.maps.android.compose.ComposeMapColorScheme
```

`GoogleMap(` の引数に `mapColorScheme` を追加する。`uiSettings` の直後に置く。

```kotlin
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                rotationGesturesEnabled = false,
            ),
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        ) {
```

- [x] **Step 13: テストとビルドを確認する**

Run: `./gradlew :feature:map:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL。`MapMarkerStyleTest` の 2 件と `MapClusterLabelTest` の 4 件がすべて成功する

- [x] **Step 14: 設計書を更新する**

`docs/design.md` の 348-354 行目の表を次で置き換える。

```markdown
| 状態 | 表現 |
|---|---|
| 訪問済（通常店舗） | 緑塗りつぶし円＋白リング |
| 未訪問（通常店舗） | 白塗り＋緑枠の円＋白リング |
| Reserve店舗 | 上記に加えて右上に琥珀のスターバッジ（訪問状態とは直交する軸） |
| 現在地 | 青塗りつぶし円 |
| クラスタ | 件数ラベル付きのピル。未訪問が過半なら中空、それ以外は緑塗り |
```

表の直後に次の段落を追加する。

```markdown
訪問状態は色相ではなく「塗りつぶしの有無」と「明暗」で表現する。これによりグレースケール表示や色覚多様性のもとでも情報が失われない。白リングは、地図タイルがダークモードで暗くなったときに濃い緑の塗りつぶしが背景へ沈むのを防ぐために全マーカーへ付ける。
```

- [x] **Step 15: コミット**

```bash
git add feature/map/src/main/java/blue/starry/onemorecoffee/feature/map/MapScreen.kt feature/map/src/main/res/drawable/star_fill.xml feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapMarkerStyleTest.kt feature/map/src/test/java/blue/starry/onemorecoffee/feature/map/MapClusterLabelTest.kt docs/design.md
git commit -m "$(cat <<'EOF'
feat(map): ピンとクラスタを塗り / 中空で表現しダークタイルに対応する

訪問状態と Reserve を直交する 2 軸として扱い、訪問済の Reserve 店舗が
Reserve であることを失う不具合を解消する。色相への依存を取り除き、
グレースケール表示でも訪問状態が判別できるようにする。
地図タイルは ComposeMapColorScheme.FOLLOW_SYSTEM に委ねる。

Co-Authored-By: Claude Fable 6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 実機検証と PR

**Files:**
- 変更なし（検証と成果物の提出のみ）

**Interfaces:**
- Consumes: Task 1〜3 のすべて
- Produces: PR

グローバルの完了条件により、before / after を識別できる証跡が必要である。before はこのブランチを切る前の `main`（`945e456`）で撮影する。

- [x] **Step 1: before のスクリーンショットを取得する**

```bash
git switch --detach 945e456
```

エミュレータを起動し、`./gradlew :app:installDebug` でこの時点のアプリを入れる。mobile-mcp で次を撮影する。

- マップ（訪問済・未訪問・Reserve・クラスタが同時に写る位置）
- リスト
- 統計
- 設定

ライトモードで撮影したあと、ダークに切り替えて同じ 4 枚を撮影する。さらにグレースケールを有効にしてマップを撮影する。

```bash
adb shell cmd uimode night yes
adb shell settings put secure accessibility_display_daltonizer_enabled 1
adb shell settings put secure accessibility_display_daltonizer 0
```

- [x] **Step 2: ブランチへ戻る**

```bash
git switch feat/dark-mode-grayscale
```

- [x] **Step 3: after のスクリーンショットを取得する**

Run: `./gradlew :app:installDebug`

Step 1 と同じ画面・同じ条件で撮影する。マップは同じ座標・同じズームにする。

あわせて、ダークモードでアプリを一度終了してから起動し、白いフラッシュが出ないことを確認する。

- [x] **Step 4: 全体の検証コマンドを実行する**

Run: `./gradlew test lint`
Expected: BUILD SUCCESSFUL。lint の指摘が出た場合は、設定変更やコメントでの抑制はせず、対応方針をユーザーに確認する

- [x] **Step 5: エミュレータの表示設定を戻す**

```bash
adb shell settings put secure accessibility_display_daltonizer_enabled 0
adb shell cmd uimode night no
```

- [ ] **Step 6: PR を作成する**

```bash
git push -u origin feat/dark-mode-grayscale
```

PR 本文には次を含める。

- 変更の概要
- `Close #18`
- before / after のスクリーンショット（ライト / ダーク / グレースケール）
- 設計書へのリンク

画像の添付は `github-image-upload` スキル（`gh image upload`）を使う。

作成後、ユーザーを Assign する。マージ可否を確認し、コンフリクトしている場合は解消する。

未完事項や未検証項目が残る場合は Draft PR とし、その旨を報告する。

---

## Self-Review

**1. Spec coverage**

| 設計書の節 | 対応するタスク |
|---|---|
| 3.1 カラースキームの切替 | Task 1 |
| 3.2 パレット | Task 1 |
| 3.3 起動時ウィンドウ背景 | Task 2 |
| 3.4 地図タイル | Task 3 Step 12 |
| 4.1 設計原則（白リング） | Task 3 Step 7, 9 |
| 4.2 ピンのマトリクス | Task 3 Step 4, 7 |
| 4.3 マーカーの色定数 | Task 3 Step 5 |
| 4.4 クラスタ | Task 3 Step 4, 9, 10 |
| 5 変更対象ファイル | File Structure に対応 |
| 6 テスト方針 | Task 3 Step 1〜4 |
| 7 検証計画 | Task 4 |

**2. Placeholder scan**

Task 1 Step 2 の「解決できない場合のみ」という分岐は、条件と対処の両方を具体的に示しているためプレースホルダではない。それ以外に TBD の類はない。

**3. Type consistency**

- `MarkerFill` は Task 3 Step 4 で定義し、同 Step 7・9・10 が消費する。
- `StoreMarkerStyle` は data class に変わり、`storeIconFor` と `storeIconCache` のキーとして使われる。
- `markerStyleFor` / `clusterStyleFor` のシグネチャは定義と呼び出しで一致している。
- 設計書が `ClusterStyle` と呼んでいた型を `MarkerFill` に統合した点は File Structure 節に明記した。
