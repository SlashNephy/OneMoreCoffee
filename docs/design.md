# スターバックス マイストアパスポート 制覇支援アプリ 設計書

## 1. 概要

アプリ名は「One More Coffee」 とする。

### 1.1 目的

スターバックスの「マイストアパスポート」制覇に向けて、未訪問店舗を効率的に把握・管理することを目的とした個人向け Android アプリ。

### 1.2 主な利用シーン

- 出先で「近くに未訪問のスタバがあるか」を確認する
- 旅行先で訪れるべき店舗を計画する
- 制覇率や進捗を眺めて達成感を得る
- 過去の訪問履歴を日記的に振り返る

### 1.3 想定ユーザー

開発者本人。公開配布は想定しない（規約上の制約のため、後述）。

Firebase Distribution や GitHub Actions による配布機能も不要。

---

## 2. 機能要件

### 2.1 コア機能

- **マップ表示**：店舗ピンを地図上に表示。訪問済/未訪問が一目で分かる
- **現在地表示**：地図上に現在地ピンを表示
- **ズーム・検索**：地図のズーム操作と店舗名による検索
- **店舗詳細**：訪問状況、最終訪問日（相対表示）、住所、営業時間など
- **外部マップ連携**：Google Maps 等の外部アプリで経路検索を起動
- **訪問履歴インポート**：WebView 経由で My Starbucks にログインし、`window.Stamp.store_all` から訪問済店舗を取得
- **手動記録**：アカウント連携を使わなくても訪問を記録可能

### 2.2 追加機能

- **統計ダッシュボード**：全国・都道府県別の制覇率、訪問ペース、完全制覇までの予測
- **ジオフェンス通知**：未訪問店舗に近接した際の通知（任意）
- **新店舗オープン通知**：開店予定店舗の取得と通知
- **訪問日記**：訪問ごとにメモ・写真を残せる

### 2.3 スコープ外（優先度低）

- 訪問頻度による視覚的区別（データ構造としては取得可能にしておく）
- レシートOCRによる自動入力
- ルート最適化（巡回セールスマン的な経路提案）
- ゲーミフィケーション（バッジ、アチーブメント）
- 端末間同期（個人利用前提のため）

---

## 3. 非機能要件

### 3.1 法務・規約対応

スターバックスのサイト規約および My Starbucks ご利用規約を確認した結果、以下のポリシーを採用。

#### 許容される範囲

- **私的使用としての店舗マスタ取得**：使用条件で「私的使用」が明示的に認められているため、個人で使う分には問題なし
- **自分の訪問履歴の取得**：My Starbucks 規約上、自分の情報を自分で取り出すことは妨げられない

#### 制約

- **店舗マスタの再配布禁止**：使用条件で「複製、配布、再公開」が禁止されているため、取得した店舗データを他者に配布しない
- **公開配布不可**：Play Store 公開等を行うと「再配布」とみなされうるため、配布は行わない（自己ビルド or 限定的な内部配布）
- **サービス妨害の回避**：取得頻度を抑え、過剰なリクエストを送らない

### 3.2 アクセスマナー

- User-Agent でアプリを識別可能にする（透明性）
- リクエスト間隔を 1.5 秒以上空ける
- 取得は月1回程度に抑える
- 深夜帯は避ける
- 失敗時は指数バックオフ

### 3.3 プライバシー

- 訪問履歴・店舗データはすべて端末ローカルに保持
- 外部送信は行わない（クラッシュレポートを除く）
- WebView でのログインはパスワードをアプリに保存しない（Cookie のみ）

---

## 4. アーキテクチャ

### 4.1 プラットフォーム

- **OS**: Android（iOS は当面対象外）
- **UI**: Jetpack Compose
- **言語**: Kotlin

### 4.2 主要技術スタック

| 領域 | 採用 |
|---|---|
| UI | Jetpack Compose |
| 地図 | Google Maps Compose（要検討：MapLibre + OSM も候補） |
| ローカル DB | Room |
| HTTP | OkHttp + kotlinx.serialization |
| 非同期処理 | Coroutines + Flow |
| バックグラウンド処理 | WorkManager |
| 位置情報・ジオフェンス | FusedLocationProviderClient + GeofencingClient |
| WebView | AndroidView でラップ + JavaScriptInterface |
| 画像読み込み | Coil |

### 4.3 構造

レイヤード構成：

```
UI (Compose) → ViewModel → Repository → DataSource (Room / Network / WebView)
```

- Repository が複数の DataSource を統合
- Flow で UI まで反応的に流す
- Compose は純粋に State を描画するだけ

---

## 5. データモデル

### 5.1 設計の基本方針

- `Store`（店舗マスタ）と `Visit`（訪問記録）を**別エンティティに分離**
- これにより、最終訪問日・訪問回数・月別ヒストグラム・日記的記録のいずれも将来的に拡張可能
- 公式店舗ID（`store_id`）が変更された場合でも訪問記録が壊れないよう、アプリ内で別途 `internalId` を発行

### 5.2 Store エンティティ

```kotlin
@Entity(tableName = "stores")
data class Store(
    @PrimaryKey val internalId: String,        // アプリ内発行の不変ID（UUID）
    val officialStoreId: String,                // スタバ公式ID（"1783"）
    val name: String,                           // "丸の内オアゾ店"
    val nameEn: String?,                        // "Marunouchi OAZO"

    // 住所
    val prefCode: String,                       // "13" (JIS X 0401)
    val prefecture: String,                     // "東京都"
    val city: String,                           // "千代田区"
    val fullAddress: String,

    // 座標（WGS84）— "location" フィールドを採用
    val latitude: Double?,                      // null許容（閉店店舗対応）
    val longitude: Double?,

    // 店舗種別
    val storeType: String,
    val isReserve: Boolean,
    val hasPublicWifi: Boolean,

    // 営業時間（曜日別）
    @Embedded val businessHours: BusinessHours,

    // 状態管理
    val status: StoreStatus,                    // OPEN / PRE_OPEN / CLOSED
    val openDate: LocalDate?,                   // PRE_OPEN の場合
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

enum class StoreStatus { OPEN, PRE_OPEN, CLOSED }

data class BusinessHours(
    val mon: DayHours?, val tue: DayHours?, val wed: DayHours?,
    val thu: DayHours?, val fri: DayHours?, val sat: DayHours?,
    val sun: DayHours?, val holiday: DayHours?,
)

data class DayHours(val open: LocalTime, val close: LocalTime)
```

### 5.3 Visit エンティティ

```kotlin
@Entity(
    tableName = "visits",
    indices = [Index(value = ["storeId", "visitedAt", "source"], unique = true)]
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: String,                        // Store.internalId への参照
    val visitedAt: Instant,
    val memo: String? = null,
    val photoUri: String? = null,
    val source: VisitSource,
)

enum class VisitSource { MANUAL, IMPORTED_STARBUCKS }
```

### 5.4 設計のポイント

- **複合ユニークインデックス**で `INSERT OR IGNORE` による安全な再インポートを実現
- 写真は URI 参照のみ DB に保持（実体はアプリ専用ストレージに保存）
- 緯度経度は nullable（閉店済み店舗で公式マスタにない場合に対応）
- `firstSeenAt` / `lastSeenAt` で店舗の存在期間を追跡し、閉店検知に利用

---

## 6. 外部データソース

### 6.1 店舗マスタ API

#### エンドポイント

```
https://hn8madehag.execute-api.ap-northeast-1.amazonaws.com/prd-2019-08-21/storesearch
```

CloudSearch ベースのエンドポイント。

#### クエリパラメータ

| パラメータ | 値 |
|---|---|
| `size` | 100（ページサイズ） |
| `q.parser` | `structured` |
| `q` | `(and ver:10000 record_type:1)` |
| `fq` | `(and data_type:'prd')` |
| `sort` | `zip_code asc,store_id asc` |
| `start` | オフセット |

#### 必須ヘッダ

- `Referer: https://store.starbucks.co.jp/`
- `User-Agent: MyStorePassport/1.0 (Android; personal use)`

#### レスポンス

```json
{
  "status": { "rid": "...", "time-ms": 1 },
  "hits": {
    "found": 2119,
    "start": 0,
    "hit": [{ "id": "...", "fields": { ... } }]
  }
}
```

#### 重要フィールド

| フィールド | 用途 | 注意 |
|---|---|---|
| `store_id` | 公式店舗ID | アプリ内では `officialStoreId` として保持 |
| `name` / `en_name` | 店舗名 | |
| `pref_code` | 都道府県コード | JIS X 0401 |
| `address_1` ~ `address_5` | 住所階層 | |
| `location` | **WGS84 緯度経度** | これを使う |
| `location_jp` | 日本測地系座標 | 約400mズレるため使わない |
| `reserve_flg` | Reserve店舗フラグ | "1" で Reserve |
| `public_wireless_service_flg` | 公衆無線LANフラグ | |
| `mon_open` / `mon_close` 等 | 曜日別営業時間 | |

### 6.2 開店予定店舗

```
https://store.starbucks.co.jp/data/latest/pre_open_store.json
```

```json
[{ "open_date": "2026-05-12", "id": "4519", "name": "...", "en_name": "..." }]
```

緯度経度を含まないため、`PRE_OPEN` 状態として保持し、地図には表示せず、リストUIの「開店予定」セクションに表示する。開店日を過ぎたらメインAPI側に出現するため、その時点で `OPEN` に昇格させる。

### 6.3 訪問履歴インポート

#### 取得元

- ログイン済みの `https://www.starbucks.co.jp/mystarbucks/mystore/`
- ページ内の `window.Stamp.store_all` 配列に訪問済店舗が格納されている

#### 1件あたりのスキーマ

```json
{
  "store_id": "1369",
  "last_visit_date": "2026-04-28 10:01:09",
  "first_visit_date": "2026-04-28 10:01:09",
  "frequency_of_visits": "1",
  "pref_code": 13,
  "name": "渋谷2丁目店",
  "is_exist": 1,
  "limited_area": 0
}
```

#### 取得方法

- WebView でユーザーがスタバアカウントにログイン
- `WebViewClient.onPageFinished` で URL が訪問履歴ページに到達したら JS を注入
- `window.Stamp.store_all` を JSON 文字列化し、JavaScriptInterface 経由で Kotlin に受け渡し
- Kotlin 側で `INSERT OR IGNORE` で Visit に追加

#### Visit への変換

`first_visit_date` と `last_visit_date` から最大 2 件の Visit レコードを生成する。両者が同一の場合は 1 件のみ。

#### 関連リソース

- スタンプ画像: `https://www.starbucks.co.jp/mystarbucks/mystore/images/stamp/{store_id}.png`
- 店舗からのメッセージ: `https://d3vgbguy0yofad.cloudfront.net/mystore/passport/json/store_message/{store_id}.json`

これらはオプショナル機能として、店舗詳細を開いた時に遅延ロード。

#### 閉店済み店舗の扱い

`is_exist == 0` の店舗は店舗マスタに存在しないが、過去の訪問記録としては保持したい。緯度経度なしで `Store` エンティティを生成し、`status = CLOSED` で保存する。

---

## 7. UI / UX

### 7.1 画面構成

ボトムナビゲーション 4 タブ構成：

```
[マップ] [リスト] [統計] [設定]
```

その他、設定からモーダルで開く画面：

- スタバアカウント連携（WebView）
- データ管理
- 通知設定

### 7.2 主要画面

| 画面 | 主要要素 |
|---|---|
| マップ | 検索バー、フィルタチップ、ピン、現在地、クラスタ、現在地FAB |
| 店舗詳細 | スタンプ画像、店舗情報、訪問ステータス、ナビボタン、訪問履歴 |
| リスト | 検索、フィルタタブ、ソート選択、店舗行（ピン状態+情報+距離） |
| 統計 | 制覇率カード、ペース予測、都道府県別バーチャート |
| 設定 | アカウント連携、データ管理、通知、表示設定 |
| アカウント連携 | 信頼担保のインフォバナー、WebView |

### 7.3 ピンの視覚言語

| 状態 | 表現 |
|---|---|
| 訪問済（通常店舗） | 緑塗りつぶし円＋白リング |
| 未訪問（通常店舗） | 白塗り＋緑枠の円＋白リング |
| Reserve店舗 | 上記に加えて右上に琥珀のスターバッジ（訪問状態とは直交する軸） |
| 現在地 | 青塗りつぶし円 |
| クラスタ | 件数ラベル付きのピル。未訪問が過半なら中空、それ以外は緑塗り |

訪問状態は色相ではなく「塗りつぶしの有無」と「明暗」で表現する。これによりグレースケール表示や色覚多様性のもとでも情報が失われない。白リングは、地図タイルがダークモードで暗くなったときに濃い緑の塗りつぶしが背景へ沈むのを防ぐために全マーカーへ付ける。

---

## 8. 主要フロー

### 8.1 初回起動

1. オンボーディング（権限要求、機能説明）
2. 店舗マスタを CloudSearch から全件取得（プログレスバー表示）
3. 開店予定店舗を取得
4. 現在地周辺にズームしてマップ表示

### 8.2 店舗マスタの定期更新

- WorkManager で月1回、Wi-Fi接続時・充電中の制約付きで実行
- 取得失敗時は前回データで動作継続
- 設定画面に「今すぐ更新」ボタンを設置

### 8.3 訪問記録の追加（手動）

1. マップまたはリストから店舗をタップ
2. 店舗詳細画面で「訪問記録を追加」をタップ
3. 日時ピッカー（デフォルトは現在時刻）、メモ、写真を入力
4. 保存して詳細画面に戻る

### 8.4 訪問履歴インポート

1. 設定 → 「アカウント連携」をタップ
2. WebView 起動、信頼担保バナー表示
3. ユーザーがスタバアカウントでログイン
4. `/mystarbucks/mystore/` 到達を検知 → JS 注入 → `window.Stamp.store_all` 取得
5. JavaScriptInterface 経由で Kotlin に JSON 受け渡し
6. 店舗マッチング（`officialStoreId`）→ 不在の場合は閉店店舗として作成
7. Visit 生成 → `INSERT OR IGNORE` で重複回避
8. 「✓ 287店舗の訪問記録をインポートしました」を表示

### 8.5 ジオフェンス通知

- ユーザーがオプトイン（バックグラウンド位置情報パーミッション要求）
- 現在地に基づき近傍 N 件の未訪問店舗のジオフェンスを動的登録（GeofencingClient の100件制限対応）
- ENTER イベントで通知発火
- 位置が大きく変わったら登録を入れ替え

### 8.6 閉店検知

```
1. 月次の店舗マスタ取得
2. 既知の OPEN 店舗で、今回の取得結果に存在しないものを検出
3. 一時的な掲載漏れの可能性があるので、即削除はせず status を CLOSED に変更
4. Visit レコードは保持
5. マップ上ではグレーアウト（設定で表示切替可）
```

---

## 9. セキュリティ考慮事項

### 9.1 WebView の安全性

- `WebViewClient.shouldOverrideUrlLoading` で starbucks.co.jp 以外への遷移をブロック
- `addJavascriptInterface` のメソッドは最小限（受信のみ、操作系は持たせない）
- `@JavascriptInterface` アノテーションを必ず付与
- HTTPS のみ許可（`MIXED_CONTENT_NEVER_ALLOW`）

### 9.2 認証情報の取り扱い

- パスワードはアプリで保存しない
- Cookie は CookieManager で永続化（次回はログインスキップ可）
- ログアウト機能で Cookie を破棄

---

## 10. オープンクエスチョン

### 10.1 設計上の検討事項

1. **Reserve 訪問済の視覚表現**：オレンジ枠＋緑塗りで充分か、もう少し工夫が必要か
2. **店舗詳細でのスタンプ画像活用**：スタバ公式画像を表示する範囲（個人利用 OK だが SNS 共有機能は微妙）
3. **店舗からのメッセージの折りたたみ**：縦に伸びるため UI 上での扱いを検討
4. **タブ構成**：4タブで足りるか、「通知フィード」を追加するか
5. **マップ実装**：Google Maps Compose vs MapLibre + OSM のトレードオフ

### 10.2 データ調査タスク

1. **`store_type` の値の種類**：1以外に何があるか（Reserve Roastery、コンセプトストア、地域共生店舗など）
2. **その他のフラグ**：ドライブスルー、座席数、設備情報などが取れるか
3. **`q.parser=structured` の他のフィルタ**：サーバーサイド絞り込みが効くか
4. **CloudSearch の `record_type`**：1以外の値の意味

### 10.3 規約上の注意点

- アプリの公開配布は行わない方針を堅持
- 取得したスタンプ画像のキャッシュ範囲（私的利用範囲内）
- 取得頻度・アクセスマナーの遵守

---

## 11. 将来の拡張可能性

データモデルが `Visit` を独立エンティティとして持っているため、以下の拡張が容易：

- 訪問頻度に応じた色分け表示
- 月別・年別の訪問ヒストグラム
- ヒートマップ表示
- 「久しく行ってない店舗」の通知
- ルート最適化（巡回セールスマン的な経路提案）
- バッジ・アチーブメント

---

## 12. 想定外の対応

### 12.1 スタバ側のサイト構造変更

- 店舗マスタ API の変更：店舗データ取得失敗 → アプリ既存データで動作継続 → アップデートで対応
- WebView 訪問履歴の取得失敗：エラー表示 + 「手動入力で記録できます」のフォールバック
- ログインフロー変更（reCAPTCHA 等）：ユーザーが手動でログインするため比較的耐性あり

### 12.2 アプリのライフサイクル

スタバ側の構造に依存する箇所が多いため、長期メンテナンスは前提としない。**手動記録機能を主軸**とし、アカウント連携・店舗マスタ取得は補助機能として位置付ける。これらが壊れてもアプリの基本機能は動作する設計を維持する。

## 13. 実装にあたっての留意点

- プロジェクトの実装にあたっては、既存プロジェクトである [Mitsubachi](https://github.com/SlashNephy/mitsubachi) のモジュールレイアウト・アーキテクチャ・を参考にすること。ローカルには `~/ghq/github.com/SlashNephy/mitsubachi` にリポジトリがクローンされているため、必要に応じて確認してよい。
- 使用する依存関係は最新のものを利用すること。動作を確認できれば、ベータ版やアルファ版のバージョンを使用してよい。
