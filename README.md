# OneMoreCoffee

OneMoreCoffee は、個人のスターバックス訪問状況を管理する Android アプリです。
店舗一覧、地図、統計、My Starbucks の訪問履歴インポートを使って、未訪問店舗や訪問済み店舗を確認できます。

## Google Maps API キー

地図画面を利用するには、リポジトリルートに `secrets.properties` を作成し、Maps SDK for Android の API キーを設定します。

```properties
MAPS_API_KEY=your_api_key
```

API キーは Google Cloud Console で次のように制限してください。

- Android アプリ制限: パッケージ名 `blue.starry.onemorecoffee` と署名証明書の SHA-1 を指定する。
- API 制限: Maps SDK for Android のみに制限する。

MVP では Places SDK や、App Check に対応している他の Google Maps Platform API は使用しません。

## ビルド

Android SDK の場所を指定して、mise 経由でテスト、lint、debug ビルドを実行します。

```sh
ANDROID_HOME=$HOME/Android/Sdk mise exec -- ./gradlew test lint assembleDebug
```
