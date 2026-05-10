# OneMoreCoffee MVP Design

## 概要

OneMoreCoffee は、スターバックスのマイストアパスポート制覇を支援する個人利用向け Android アプリである。

MVP では、店舗マスタを取得し、My Starbucks の訪問履歴を WebView からインポートして、現在営業中の店舗に対する訪問状況をマップ、リスト、統計で確認できる状態を目指す。手動訪問記録や配布機能は MVP には含めない。

## MVP スコープ

### 含めるもの

- 店舗マスタ API からの全件取得
- Room への店舗と訪問記録の保存
- WebView による My Starbucks ログイン
- `window.Stamp.store_all` からの訪問履歴インポート
- `store_id` による店舗と訪問履歴の突合
- Google Maps Compose によるマップ表示
- 訪問済み、未訪問、Reserve 店舗のピン表示
- 店舗名と住所によるリスト検索
- 訪問済み、未訪問の絞り込み
- 店舗詳細表示
- Google Maps での経路検索 intent
- 設定画面からの店舗データ更新、訪問履歴インポート、ログアウト
- 全国の訪問済店舗数、現在店舗数、制覇率の表示
- インポート結果で、店舗マスタに存在しない訪問履歴件数を表示

### 含めないもの

- 手動訪問記録
- 開店予定店舗 `pre_open_store.json` の取得
- Places SDK / Places API
- ジオフェンス通知
- 新店舗通知
- 写真付き訪問日記
- 都道府県別の詳細統計
- Firebase Distribution
- Play Store 公開などの配布系機能

## Google Maps API Key

MVP では Google Maps Compose と Maps SDK for Android を使う。

Maps SDK for Android の API key は Firebase App Check による保護対象として扱える公式手順が確認できないため、MVP では次の制限を必須にする。

- API key は `secrets.properties` と `secrets.defaults.properties` で管理する
- Google Cloud 側で Android アプリ制限を設定する
- Android アプリ制限には package name と SHA-1 certificate fingerprint を使う
- Google Cloud 側で API 制限を設定し、Maps SDK for Android のみを許可する
- Places SDK など App Check 対応済みの Google Maps Platform API を追加する場合は、Firebase App Check を導入してから使う

Maps API key が未設定の場合、マップ画面は設定不足の状態を表示し、アプリ全体は落とさない。

## アーキテクチャ

Mitsubachi を参考にしたマルチモジュール構成にする。ただし MVP の速度を優先し、必要最小限の分割に留める。

### モジュール

- `:app`
  - Application、MainActivity、Navigation、Theme、DI 起点
- `:core:common`
  - serializer、日時、共通 utility
- `:core:domain`
  - domain model、repository interface、use case
- `:core:data`
  - Room、Ktor、WebView import parser、repository 実装
- `:core:ui`
  - 共通 Compose component、icon、theme helper
- `:feature:map`
  - マップ画面、店舗詳細 bottom sheet
- `:feature:list`
  - リスト検索、店舗詳細への導線
- `:feature:stats`
  - MVP 統計
- `:feature:settings`
  - データ更新、訪問履歴インポート、ログアウト
- `:feature:import`
  - WebView ログイン、訪問履歴インポート画面

### 技術方針

- UI は Jetpack Compose と Navigation 3 系を使う
- DI は Hilt を使う
- 非同期処理は Coroutines と Flow を使う
- HTTP は Ktor と kotlinx.serialization を使う
- DB は Room を使う
- Firebase App Distribution、Analytics、Crashlytics は MVP では入れない
- WorkManager の月次自動更新は MVP では入れず、手動更新を先に実装する

依存方向は、feature が `core:domain` と `core:ui` に依存し、Room や Ktor の実装は `core:data` に閉じる形にする。UI は Room や Ktor を直接知らない。

## データモデル

### Store

`Store` は現在の店舗マスタ API に存在する店舗だけを保存する。

- PK は `id: String`
- `id` の値は公式 `store_id`
- `latitude` と `longitude` は `NOT NULL`
- `rawJson: String` に店舗 API の元 JSON を保存する
- `StoreStatus` は持たない
- 閉店済みやマスタ外の店舗を表す行は作らない

展開するカラム:

- `id`
- `name`
- `nameEn`
- `prefCode`
- `prefecture`
- `fullAddress`
- `latitude`
- `longitude`
- `isReserve`
- `lastSeenAt`

営業時間や詳細フラグは MVP では `rawJson` に留める。

### Visit

`Visit` は公式 `store_id` と訪問日を保存する。`Store` への外部キー制約は張らない。

- `storeId: String`
- `visitedOn: LocalDate`
- `source: VisitSource`
- unique index は `storeId + visitedOn`
- `source` は unique index に含めない
- MVP では `memo` と `photoUri` は持たない

`VisitSource` は enum として定義し、MVP では `IMPORTED_STARBUCKS` のみを使う。

`Visit.storeId` に対応する `Store` が存在しない場合、その訪問履歴は閉店済みまたはマスタ外店舗として扱う。MVP では専用画面には表示せず、インポート結果で件数だけ表示する。

## 店舗更新

店舗更新は CloudSearch API から `record_type:1` の店舗を全件取得する。

- `start` offset でページングする
- `store_id` を `Store.id` として upsert する
- `location` を WGS84 として使う
- `location_jp` は使わない
- `location` がない、または壊れているレコードは保存せず skipped として数える
- リクエスト間隔は 1.5 秒以上空ける
- 失敗時は指数バックオフする

MVP では、表示対象を最新取得結果に含まれる店舗に寄せる。閉店履歴を独立して追跡する機能は持たない。

## 訪問履歴インポート

`feature:import` の WebView で My Starbucks にログインし、訪問履歴ページから `window.Stamp.store_all` を取得する。

- WebView の遷移先は `starbucks.co.jp` のみ許可する
- HTTPS のみ許可する
- mixed content は許可しない
- JavaScriptInterface は JSON 受信メソッドのみを持つ
- `/mystarbucks/mystore/` 到達後に JS を注入する
- `window.Stamp.store_all` を JSON 文字列として Kotlin 側に渡す
- kotlinx.serialization で import DTO に parse する

Visit への変換:

- `first_visit_date` と `last_visit_date` を `LocalDate` に丸める
- 同じ日の場合は 1 件だけ保存する
- 異なる日の場合は最大 2 件の Visit を作る
- `frequency_of_visits` は MVP の Visit には反映しない
- 店舗マスタに存在しない `store_id` でも Visit は保存する
- 重複は `storeId + visitedOn` unique index で防ぐ

## UI

Bottom navigation は `[マップ] [リスト] [統計] [設定]` の 4 タブにする。

### マップ

- Google Maps Compose で表示する
- 店舗マスタが空なら更新導線つき empty state を表示する
- 現在地権限があれば現在地に寄せられるようにする
- ピンは訪問済み、未訪問、Reserve を区別する
- 店舗タップで店舗詳細 bottom sheet を開く

### リスト

- 店舗名と住所で検索できる
- 訪問済み、未訪問で絞り込める
- 店舗行には店舗名、住所、訪問状態、最終訪問日を表示する

### 店舗詳細

- 店舗名
- 住所
- 訪問状態
- 最終訪問日
- 訪問回数
- 経路検索ボタン

### 統計

- 現在店舗数
- 訪問済店舗数
- 制覇率

分母は現在の店舗マスタ件数、分子は現在店舗マスタに join できる訪問済店舗数にする。

### 設定

- 店舗データ更新
- 訪問履歴インポート
- ログアウト
- Google Maps API key の設定注意

### インポート

- 信頼担保バナーを表示する
- WebView でログイン操作を行う
- インポート成功時は追加件数、重複件数、マスタ外件数を表示する

## エラー処理

- 店舗更新失敗時、既存データがあれば継続する
- 店舗データがない状態で店舗更新に失敗した場合は error state を出す
- 店舗 API の一部レコードが不正な場合は skipped として集計し、全体処理は継続する
- WebView ログイン未完了時はユーザー操作待ちにする
- `window.Stamp.store_all` が存在しない場合は、ページ構造が変わった可能性を表示し、再試行導線を出す
- import JSON の全体 parse に失敗した場合は中断する
- import JSON の一部だけが不正な場合は、失敗件数を出して処理を継続する
- Maps API key が未設定の場合は、マップだけ設定不足状態にする

ログとエラーメッセージは英語で記述する。

## テスト

### core:data

- CloudSearch response parser
- `location` parser
- Store upsert
- import DTO parser
- `first_visit_date` と `last_visit_date` の日単位重複排除
- `storeId + visitedOn` unique index

### core:domain

- 訪問済判定
- 統計計算
- マスタ外 Visit を統計から除外する処理

### feature

- ViewModel の empty / loading / success / error state
- インポート結果表示

### 検証コマンド

実装時はコミット前に次を実行する。

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```
