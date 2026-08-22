# アプリアイコンのアセット

ランチャーアイコンの図像の出所は `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` の Adaptive Icon ただ一つで、このディレクトリに図像そのものは置いていない。ここにあるのは、そこからレガシー mipmap を焼き出すための道具だけ。

| ファイル | 役割 |
| --- | --- |
| `adaptive_icon_to_svg.py` | Adaptive Icon の background / foreground の参照をたどってリソースを解決し、1 枚の SVG に変換する。想定外の要素・属性が現れたらエラーで落ちる |
| `generate_mipmaps.sh` | 上記を経由して `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher,ic_launcher_round}.png`（48 / 72 / 96 / 144 / 192 px）を再生成する |

レイヤーの中身を直した場合も、`ic_launcher.xml` のレイヤー参照そのものを差し替えた場合も、再生成すればレガシー側が追従する。ラスタライズ用の SVG を別に持たないので、図像が二重管理になることはない。

変換器が対応するのは、本アイコンが実際に使っている Vector Drawable の構成に限る。

- `<group>` の `pivot` / `scale` / `rotation` / `translate` は SVG の `transform` に翻訳する。Android の合成順（`T(pivot + translate) · R · S · T(-pivot)`）をそのまま SVG の transform リストの並びに写している。
- `strokeColor` などのストロークも翻訳する。SVG の `fill` の既定値は黒、Android の既定値は塗り無しなので、`android:fillColor` を持たないパスには `fill="none"` を明示している。
- 背景が `<shape>`（単色の Shape Drawable）の場合は、ビューポート全面の `<rect>` に落とす。

グラデーション、`<clip-path>`、`#AARRGGBB` の色などが現れた場合は、黙って無視せずエラーで落ちる。落ちたときは変換器を拡張するか、ラスタライズの方法自体を見直す合図。

なお `<monochrome>` はランチャーがテーマアイコン用に単色化して使うレイヤーで、ラスタには現れないため無視している。

## レガシー mipmap を出荷している理由

minSdk は 26 なので、端末上は `mipmap-anydpi-v26` の Adaptive Icon だけで足りる。レガシー mipmap は端末のためではなく、APK からアイコンを取り出す外部のコンシューマ（Firebase App Distribution など）のために置いている。それらは Adaptive Icon の XML を解釈せず、密度別のビットマップを探すため、これが無いとアイコンが表示されない。

同じ理由で出力は PNG にしている。WebP を解釈できないコンシューマがあるため。

`ic_launcher_round` も出しているのは、`AndroidManifest.xml` が `android:roundIcon="@mipmap/ic_launcher_round"` を参照しているため。参照されているリソースの密度別候補だけを埋めている。

端末上の見た目には影響しない。`android:icon` / `android:roundIcon` の解決では、API 26 以降は常に `mipmap-anydpi-v26` が優先される。

## 使い方

`rsvg-convert`（librsvg）と `magick`（ImageMagick 7）が必要。

```bash
./docs/superpowers/assets/icon/generate_mipmaps.sh
```

Adaptive Icon を変更したら再実行して、出力をコミットする。同じ入力からはバイト単位で同じ PNG が出る。

変更が反映されたことは、生成された PNG を見るか、`git status` に差分が出ることで確認できる。レイヤー参照の差し替えが効いているかを単体で確かめるなら、SVG を直接覗くのが早い。

```bash
python3 docs/superpowers/assets/icon/adaptive_icon_to_svg.py app/src/main/res
```
