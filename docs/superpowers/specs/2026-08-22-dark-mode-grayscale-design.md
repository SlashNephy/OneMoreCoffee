# ダークモード対応・グレースケールでの視認性向上 設計書

- Issue: [#18](https://github.com/SlashNephy/OneMoreCoffee/issues/18)
- 作成日: 2026-08-22

## 1. 背景と目的

現状のアプリはライトテーマ固定である。`OneMoreCoffeeTheme` は `lightColorScheme` のみを持ち、`isSystemInDarkTheme()` による分岐が存在しない。`AppTheme` の親も `@android:style/Theme.Material.Light.NoActionBar` である。

また、マップ上の店舗ピンは訪問状態を**色相のみ**で表現している。

| 状態 | 現状の色 | 相対輝度 |
|---|---|---|
| 訪問済 | `#2E7D32` | 約 0.18 |
| 未訪問 | `#C62828` | 約 0.15 |
| Reserve | `#6A1B9A` | 約 0.09 |

訪問済と未訪問は色相こそ正反対だが相対輝度がほぼ同値であり、グレースケール変換（実質的に輝度への射影）を通すと区別できなくなる。端末を Digital Wellbeing の「おやすみ時間モード」などでグレースケール表示にすると、マップが機能しなくなる。

本対応の目的は次の 2 点である。

1. システムのダークモード設定に追従し、暗所での視認性と OS との一貫性を確保する
2. マップの視覚表現から色相への依存を取り除き、グレースケール表示でも情報が失われないようにする

## 2. スコープ

### 対象

- `OneMoreCoffeeTheme` のダークカラースキーム対応
- 起動時ウィンドウ背景のダーク対応
- Google Maps の地図タイルのダーク対応
- マップピン・クラスタの視覚言語の刷新
- `docs/design.md` 7.3 節の更新

### 対象外

| 項目 | 理由 |
|---|---|
| 設定画面でのテーマ手動切替 | システム追従で目的を満たす。DataStore の導入はこの Issue の範囲を超える |
| WebView（ログイン画面）のダーク化 | `isAlgorithmicDarkeningAllowed` はスターバックス側の DOM を強制反転させる。インポート導線を壊すリスクに対して機能上の利得がない。ライトのまま据え置く |
| README のスクリーンショット差し替え | 別途対応する |
| `minSdk` の引き上げ | 後述のとおり `values-night` で解決するため不要 |

## 3. テーマ基盤

### 3.1 カラースキームの切替

`core/ui/src/main/java/blue/starry/onemorecoffee/core/ui/OneMoreCoffeeTheme.kt` に `darkColorScheme` を追加し、`isSystemInDarkTheme()` で選択する。

Dynamic Color（Material You）は採用しない。壁紙から配色を生成すると基調色 `#006241` が端末ごとに変わり、[app-icon-design](2026-08-21-app-icon-design.md) で定めたブランド方針と衝突するうえ、ピンとテーマの輝度関係を設計できなくなるためである。

### 3.2 パレット

ライト側は現行の 5 スロットの値を維持し、既定の紫系フォールバックが混ざらないよう派生スロットを明示する。

| スロット | ライト | ダーク |
|---|---|---|
| `primary` | `#006241` | `#6ADBA8` |
| `onPrimary` | `#FFFFFF` | `#003825` |
| `primaryContainer` | `#8FF8C4` | `#00513A` |
| `onPrimaryContainer` | `#002115` | `#86F8C3` |
| `secondary` | `#C98A3B` | `#E7BE7E` |
| `onSecondary` | `#3B2708` | `#432C05` |
| `secondaryContainer` | `#FFDDB0` | `#5E421A` |
| `onSecondaryContainer` | `#2A1800` | `#FFDDB0` |
| `tertiary` | `#2E5C8A` | `#9FC9FF` |
| `onTertiary` | `#FFFFFF` | `#003257` |
| `background` | `#F8F7F4` | `#101410` |
| `onBackground` | `#1A1C19` | `#E1E3DD` |
| `surface` | `#FFFFFF` | `#101410` |
| `onSurface` | `#1A1C19` | `#E1E3DD` |
| `surfaceContainer` | `#EFEEE9` | `#1C201B` |
| `surfaceVariant` | `#DCE5DB` | `#3F4A42` |
| `onSurfaceVariant` | `#414942` | `#BFC9BE` |
| `outline` | `#717972` | `#89938B` |
| `outlineVariant` | `#C1C9BF` | `#3F4A42` |
| `error` | `#BA1A1A` | `#FFB4AB` |
| `onError` | `#FFFFFF` | `#690005` |
| `errorContainer` | `#FFDAD6` | `#93000A` |
| `onErrorContainer` | `#410002` | `#FFDAD6` |
| `tertiaryContainer` | `#D2E4FF` | `#17497B` |
| `onTertiaryContainer` | `#001C38` | `#D2E4FF` |
| `surfaceContainerLowest` | `#FFFFFF` | `#0B0F0B` |
| `surfaceContainerLow` | `#F5F4EF` | `#181C18` |
| `surfaceContainerHigh` | `#E9E8E3` | `#262B25` |
| `surfaceContainerHighest` | `#E3E2DD` | `#313630` |
| `surfaceDim` | `#DDDCD7` | `#101410` |
| `surfaceBright` | `#F8F7F4` | `#363A35` |
| `inverseSurface` | `#2F312D` | `#E1E3DD` |
| `inverseOnSurface` | `#F0F1EB` | `#2F312D` |
| `inversePrimary` | `#6ADBA8` | `#006241` |

ダーク側の `primary` に `#006241` をそのまま使わないのは、暗いサーフェス上でコントラスト比が不足するためである。同一色相のまま明度を上げたトーンに置き換える。

### 3.3 起動時ウィンドウ背景

`app/src/main/res/values-night/themes.xml` を新規作成し、`AppTheme` の夜間版を定義する。

```xml
<resources>
    <style name="AppTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
    </style>
</resources>
```

これを省くと、ダークモードでの起動時に Compose が最初のフレームを描くまでの間だけ白いウィンドウが表示される。

`minSdk = 26` のため `@android:style/Theme.DeviceDefault.DayNight`（API 29 以降）は使用できない。ただし仮に `minSdk` を引き上げたとしても `values-night` を使う方が望ましい。`DayNight` のウィンドウ背景は DeviceDefault のグレーであり、本アプリのダーク面 `#101410` と一致しないため、白いフラッシュがグレーのフラッシュに変わるだけだからである。`windowBackground` を明示することでフラッシュ自体を目立たなくする。

ライト側の `values/themes.xml` にも同じく `windowBackground` を明示する。

`enableEdgeToEdge()` は引数なしでもリソース設定（`uiMode`）からダーク判定を行い、システムバーのアイコン色を切り替えるため追加対応は不要である。

### 3.4 地図タイル

`maps-compose` は 8.4.0 であり、`GoogleMap` コンポーザブルが `mapColorScheme: ComposeMapColorScheme` を受け取る。`MapStyleOptions` による JSON スタイル定義は不要である。

```kotlin
GoogleMap(
    mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
    // ...
)
```

## 4. マップの視覚言語

### 4.1 設計原則

色相に情報を載せない。訪問状態は**塗りつぶしの有無**（＝形の差）と**明暗**（＝輝度の差）の 2 つで表現する。この 2 つはいずれもグレースケール変換後も残る。

すべてのピンとクラスタに白の外周リングを付ける。3.4 により地図タイル自体が暗くなるため、濃い緑の塗りつぶしは暗いタイル上で輪郭を失う。白リングを常時付けておけば明暗どちらのタイルでも輪郭が立つ。

この設計には実装上の副次的な利点がある。ビットマップがテーマに依存しなくなるため、`storeIconCache` および `clusterIconCache` のキーにテーマの次元を追加する必要がなく、レンダラに `isSystemInDarkTheme` を引き回さずに済む。

### 4.2 ピンのマトリクス

訪問状態と Reserve は**直交する 2 軸**として扱う。

|  | 通常店舗 | Reserve 店舗 |
|---|---|---|
| **訪問済** | 緑塗り＋白カップ＋白リング | 左に加えて右上にスターバッジ |
| **未訪問** | 白塗り＋緑の太枠＋緑カップ | 左に加えて右上にスターバッジ |

現状のコードは次のような排他的な `when` になっている。

```kotlin
val style = when {
    store.isVisited -> StoreMarkerStyle.Visited
    store.isReserve -> StoreMarkerStyle.Reserve
    else -> StoreMarkerStyle.Unvisited
}
```

このため**訪問済の Reserve 店舗は Reserve であることを失い、通常の訪問済ピンとして描画される**。本対応はこの潜在的な欠陥も解消する。

### 4.3 マーカーの色定数

テーマに依存しない固定値として定義する。

| 用途 | 値 |
|---|---|
| 訪問済ピンの塗り | `#006241` |
| 訪問済ピンのアイコン | `#FFFFFF` |
| 未訪問ピンの塗り | `#FFFFFF` |
| 未訪問ピンの枠・アイコン | `#006241` |
| 外周リング | `#FFFFFF` |
| Reserve バッジの塗り | `#C98A3B` |
| Reserve バッジの前景 | `#3B2708` |
| Reserve バッジの枠 | `#FFFFFF` |

寸法は現行の 34dp 円を踏襲する。外周リングは 2dp、未訪問の枠は 3dp、バッジは直径 16dp・枠 2dp とする。

Reserve バッジ用に Material Symbols の star（fill）を `feature/map/src/main/res/drawable/star_fill.xml` として追加する。

### 4.4 クラスタ

現状は「未訪問が過半なら赤 `#C62828`、そうでなければ緑 `#00704A`」という色分けである。色による前注意的な区別という**機能は維持し**、その担い手を色相から輝度と塗りへ移す。地図一面のクラスタを眺めたとき、色は読解を挟まずに拾える前注意的な手がかりであり、`12+ (5)` と `20+` を読み分けるのとは認知コストが異なるためである。

ただし色相ではなく、ピンと同じ「中空 / 塗り」の語彙に載せ替える。輝度と閉じた領域の有無もまた前注意的に処理される視覚特徴であり、かつグレースケール変換後も残る。

| 条件 | 表現 |
|---|---|
| 未訪問が過半 | 白塗り＋緑の太枠＋緑のラベル（中空） |
| それ以外 | 緑塗り＋白のラベル＋白リング |

あわせて `ClusterDefaultColor` と `ClusterUnvisitedMajorityColor` の 2 定数は削除し、ピンと共通の `MarkerBrandColor`（`#006241`）に集約する。`ClusterDefaultColor` の `#00704A` はスターバックスの公式グリーンであり、[app-icon-design](2026-08-21-app-icon-design.md) で不使用と定めた方針との未整合が残っていた箇所である。視覚的な差はほとんどなく、これは視認性の改善ではなく方針の整合を取るための変更である。

`buildClusterLabel` の出力（`12+ (5)` 形式）は変更しない。

## 5. 変更対象ファイル

| ファイル | 変更内容 |
|---|---|
| `core/ui/.../OneMoreCoffeeTheme.kt` | `darkColorScheme` 追加、`isSystemInDarkTheme()` 分岐、ライト側スロットの明示 |
| `app/src/main/res/values/themes.xml` | `windowBackground` の明示 |
| `app/src/main/res/values-night/themes.xml` | 新規。夜間版 `AppTheme` |
| `app/src/main/res/values/colors.xml` ほか | 新規。`window_background`（`values-night` 版も） |
| `feature/map/.../MapScreen.kt` | `mapColorScheme` 指定、マーカー描画の刷新、`markerStyleFor` / `clusterStyleFor` の切り出し |
| `feature/map/src/main/res/drawable/star_fill.xml` | 新規。Reserve バッジ用アイコン |
| `feature/map/src/test/.../MapClusterLabelTest.kt` | `clusterFillColor` のテストを `clusterStyleFor` に追随 |
| `feature/map/src/test/.../MapMarkerStyleTest.kt` | 新規。`markerStyleFor` の 2×2 テスト |
| `docs/design.md` | 7.3 節「ピンの視覚言語」の更新 |

## 6. テスト方針

`Bitmap` の生成そのものはユニットテストに向かないため、分岐ロジックを純関数として切り出して検証する。

```kotlin
internal fun markerStyleFor(isVisited: Boolean, isReserve: Boolean): StoreMarkerStyle

internal fun clusterStyleFor(totalCount: Int, visitedCount: Int): MarkerFill
```

`markerStyleFor` は 4 通りの入力すべてを table test で押さえる。これは 4.2 に記した「訪問済の Reserve が Reserve でなくなる」という実在の欠陥に対する回帰テストであり、定数を書き写すだけのテストではない。

`clusterStyleFor` は `clusterFillColor` の改名にあたる。返り値は `Int` から、ピンの塗り分けと共通の `MarkerFill` enum に変わる。ピンとクラスタで別々の型を持たせず、同じ enum に統合したことで「中空 / 塗り」という語彙が両者で一致していることが型としても保証される。既存のテスト済みの挙動はそのまま存続する。

描画結果そのものは 7 の実機検証で確認する。

## 7. 検証計画

1. エミュレータを起動し、mobile-mcp でライト・ダーク双方の 4 タブ（マップ / リスト / 統計 / 設定）と店舗詳細シートのスクリーンショットを取得する
2. マップは訪問済・未訪問・Reserve・クラスタがすべて画面に入る位置で撮影する
3. グレースケールは開発者オプションの「色空間をシミュレート」→「全色盲」で有効化し、同じ画面を再撮影する
4. before / after を `github-image-upload` スキル（`gh image upload`）で PR に添付する
5. リポジトリ規定の lint・test を実行する

エミュレータの起動手順はリポジトリ既存の手順に従う。

## 8. 未解決事項

なし。
