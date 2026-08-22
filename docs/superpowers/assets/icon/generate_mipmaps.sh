#!/usr/bin/env bash
# レガシー mipmap（Adaptive Icon を解釈しない消費者向け）を再生成する。
#
# 図像の出所は app/src/main/res の Adaptive Icon（mipmap-anydpi-v26/ic_launcher.xml）
# ただ一つ。adaptive_icon_to_svg.py が background / foreground の参照をたどって
# SVG 化するので、レイヤーの中身を直しても参照先を差し替えても、このスクリプトの
# 再実行だけでレガシー側が追従する。
#
# 出力は WebP ではなく PNG。Firebase App Distribution など APK からアイコンを
# 取り出す外部のコンシューマには WebP を解釈できないものがあるため。
set -euo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
res="$dir/../../../../app/src/main/res"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Adaptive Icon の 108dp キャンバスのうち、マスク後に見えるのは中央 72dp。
# 768 * 108 / 72 = 1152 で描いてから中央 768px を切り出すと、実機と同じ寸法比になる。
canvas=1152
visible=768

python3 "$dir/adaptive_icon_to_svg.py" "$res" > "$tmp/icon.svg"
rsvg-convert -w "$canvas" -h "$canvas" "$tmp/icon.svg" -o "$tmp/canvas.png"
magick "$tmp/canvas.png" -gravity center -crop "${visible}x${visible}+0+0" +repage "$tmp/visible.png"

# ランチャー外のコンシューマはマスクをかけないので、四隅は自前で丸めて透過させる。
# 半径 210/768 は Adaptive Icon のスクワークルマスクに近い比率。
magick "$tmp/visible.png" \
  \( -size "${visible}x${visible}" xc:none -fill white \
     -draw "roundrectangle 0,0 $((visible - 1)),$((visible - 1)) 210,210" \) \
  -alpha set -compose DstIn -composite "$tmp/ic_launcher.png"

# AndroidManifest.xml が android:roundIcon を参照しているので、円形マスク版も出す。
# 中心は画素の中間（767/2 = 383.5）で、半径はそこから縁まで。
center="$(awk -v v="$visible" 'BEGIN { print (v - 1) / 2 }')"
magick "$tmp/visible.png" \
  \( -size "${visible}x${visible}" xc:none -fill white \
     -draw "circle $center,$center $center,0" \) \
  -alpha set -compose DstIn -composite "$tmp/ic_launcher_round.png"

emit() { # $1=density $2=size
  mkdir -p "$res/mipmap-$1"
  for name in ic_launcher ic_launcher_round; do
    # -depth 8: 既定の 16bit は情報量に見合わずファイルが太るだけなので 8bit に落とす
    # -strip: png:tIME（生成時刻）を落とし、再生成がバイト単位で再現するようにする
    magick "$tmp/$name.png" -resize "$2x$2" -depth 8 -strip "$res/mipmap-$1/$name.png"
  done
  echo "wrote mipmap-$1/{ic_launcher,ic_launcher_round}.png (${2}px)"
}

emit mdpi 48
emit hdpi 72
emit xhdpi 96
emit xxhdpi 144
emit xxxhdpi 192
