# アプリアイコン刷新 設計

## 背景

現行のランチャーアイコンはブラウン背景 (`#5D4037`) に白いカップと湯気という構成で、アプリ内テーマ (`OneMoreCoffeeTheme.kt` の `primary = #006241`) と色が一致していない。
また Android 13 以降のテーマアイコン (`<monochrome>`) に未対応で、レガシー用の `drawable/ic_launcher.xml` が参照されないまま残っている。

## 制約

- 本アプリは**非公式**アプリである。サイレン（人魚）ロゴ、"Starbucks" ワードマーク、公式グリーン `#00704A` は使用しない。
- 公開リポジトリのため、成果物に非公開情報を含めない。
- `minSdk = 26` (`app/build.gradle.kts:55`)、`compileSdk = 36`。

## デザイン

### コンセプト

「地図 × コーヒーの融合」。白いマップピンの中にコーヒー豆を配置し、店舗を巡って記録するアプリであることを 1 つの図形で表す。

### 配色

| 役割 | 色 | 根拠 |
|---|---|---|
| 背景 | `#006241` | `core/ui/.../OneMoreCoffeeTheme.kt:9` の `primary` と一致させ、アイコンとアプリ内 UI の色を揃える |
| 前景 | `#FFFFFF` | ピンおよび豆の割れ目 |

### 寸法（108dp キャンバス、検討時の a3 案 + 割れ目微増）

- ピン: ティアドロップ型。y = 22〜86 に収め、Adaptive Icon のセーフゾーン（中央 直径 66dp、y = 21〜87）内に配置する。
- 豆: 楕円 rx = 11.5 / ry = 16.5、中心 (54, 46)、**-45° 傾ける**。
- 割れ目: 太さ **4.0**、`round` キャップ。
  - 検討時の a3 案は 3.6 だったが、36px 表示で 1.2px 相当となり消失するため 4.0 に微増した。角度と豆の大きさは a3 のまま。
- 豆とピン輪郭の間の白い余白は約 5〜6 を確保する。5 を下回ると小サイズでアンチエイリアスにより境界が滲む。

### レイヤ構成

前景 drawable は**白いパスのみ**で構成する。豆は緑で塗らず `fillType="evenOdd"` による**実際の透明な穴**とし、割れ目はその穴を跨ぐ白いストロークとして上に重ねる。

この構成により、同一ファイルをそのまま `<monochrome>` に流用できる。テーマアイコンでは非透明ピクセルのみが前景色に置換されるため、豆を不透明色で塗ると豆が消えてしまう。

## 実装

### `app/src/main/res/drawable/ic_launcher_foreground.xml`（全面差し替え）

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="M54,22 C40.2,22 29,33.2 29,47 C29,64.5 54,86 54,86 C54,86 79,64.5 79,47 C79,33.2 67.8,22 54,22 Z M42.333,34.333 a11.5,16.5 -45 1,0 23.334,23.334 a11.5,16.5 -45 1,0 -23.334,-23.334 Z" />
    <group
        android:pivotX="54"
        android:pivotY="46"
        android:rotation="-45">
        <path
            android:pathData="M54,30.5 C47.5,37 60.5,55 54,61.5"
            android:strokeColor="#FFFFFF"
            android:strokeLineCap="round"
            android:strokeWidth="4" />
    </group>
</vector>
```

補足:

- `VectorDrawable` には `<ellipse>` 要素がないため、豆は円弧 2 本の `pathData` で表現する。楕円の傾きは弧コマンドの x-axis-rotation (`-45`) で指定する。
- 弧の端点 (42.333, 34.333) / (65.667, 57.667) は、中心 (54, 46) から (0, ±16.5) を -45° 回転して得た座標である。
- 割れ目は独立したパスなので `<group>` の `rotation` で回転できる。

### `app/src/main/res/drawable/ic_launcher_background.xml`

`android:color` を `#5D4037` → `#006241` に変更する。

### `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml`

両ファイルに `<monochrome android:drawable="@drawable/ic_launcher_foreground" />` を追加する。
`<monochrome>` は `compileSdk 33` 以上で利用でき、古い端末では無視される。

### `app/src/main/res/drawable/ic_launcher.xml`（削除）

未参照のため削除する。根拠:

- `AndroidManifest.xml:8-10` は `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round` を参照しており、`@drawable/ic_launcher` を参照していない。
- `drawable/` と `mipmap/` は別のリソース名前空間である。
- `mipmap-anydpi-v26` 以外の mipmap ディレクトリが存在せず、`minSdk = 26` のため常に `anydpi-v26` が選択される。

## 検証

1. `./gradlew :app:lintDebug` を通す。
2. エミュレータにインストールし、ランチャーでアイコンを確認する。
   - before / after のスクリーンショットを取得する。
   - 円形マスクと角丸マスクの両方で先端が欠けていないことを確認する。
3. Android 13 以降のテーマアイコン（設定 → 壁紙とスタイル → テーマアイコン ON）で、豆と割れ目が保たれることを確認する。
4. 通知領域やアプリ情報画面など、小サイズ表示で割れ目が消えていないことを確認する。
5. 弧コマンドの `x-axis-rotation` と `evenOdd` の組み合わせは目視でないと誤りに気付きにくいため、必ず実描画で確認する。

## 対象外

- アプリ内のアイコン (`settings.xml`, `map_search.xml` 等) は変更しない。
- README のスクリーンショットは更新しない（ランチャーアイコンは写っていない）。
- Play ストア用の 512px アイコンは作成しない（私的利用のため配信しない）。
