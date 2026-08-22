#!/usr/bin/env python3
"""Adaptive Icon（`mipmap-anydpi-v26/ic_launcher.xml`）を 1 枚の SVG に変換する。

ランチャーアイコンの図像の出所は Adaptive Icon ただ一つであり、ラスタライズ用に
別途 SVG を手で維持すると二重管理になる。`ic_launcher.xml` の background /
foreground の参照をたどってリソースを解決し、SVG に載せ替える。レイヤーの参照先を
差し替えても、ここを通せばラスタも追従する。

Vector Drawable の `android:pathData` は SVG のパス文法そのものなので、パスと塗り色を
読み出して載せ替えるだけで図像が完全に一致する。`<group>` の変形とストロークも
SVG の対応する表現に翻訳する。

対応するのは本アイコンが実際に使っている構成に限る。グラデーション、<clip-path>、
`@color/` 参照でない色などが現れた場合は黙って無視せずエラーにする。ここで落ちた
ときは、変換器を拡張するか、ラスタライズの方法自体を見直す合図。

monochrome レイヤーはランチャーがテーマアイコン用に単色化して使うものでラスタには
現れないため、意図的に無視する。
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"

SUPPORTED_VECTOR_ATTRS = {"width", "height", "viewportWidth", "viewportHeight"}
SUPPORTED_GROUP_ATTRS = {
    "name",
    "pivotX",
    "pivotY",
    "rotation",
    "scaleX",
    "scaleY",
    "translateX",
    "translateY",
}
SUPPORTED_PATH_ATTRS = {
    "name",
    "pathData",
    "fillColor",
    "fillType",
    "strokeColor",
    "strokeWidth",
    "strokeLineCap",
    "strokeLineJoin",
    "strokeMiterLimit",
}
SUPPORTED_LAYER_ATTRS = {"drawable"}
SUPPORTED_SHAPE_ATTRS = {"shape"}
SUPPORTED_SOLID_ATTRS = {"color"}

# Android は #AARRGGBB、SVG/CSS は #RRGGBBAA とアルファの位置が逆なので、
# 8 桁の値をそのまま SVG に流すと色か透明度が変わる。6 桁だけ受け付ける。
RGB = re.compile(r"^#[0-9A-Fa-f]{6}$")

LINE_CAPS = {"butt", "round", "square"}
LINE_JOINS = {"miter", "round", "bevel"}


def fail(message: str) -> None:
    sys.exit(f"adaptive_icon_to_svg: {message}")


def local(name: str) -> str:
    return name[len(ANDROID):] if name.startswith(ANDROID) else name


def check_attrs(element: ET.Element, supported: set[str], label: str) -> None:
    for raw in element.attrib:
        # aapt:attr のような別名前空間の属性はインラインリソース定義などを持ち込む。
        # 黙って無視すると図像が食い違うので、android 名前空間以外はすべて拒否する。
        if not raw.startswith(ANDROID):
            fail(f"unsupported non-android attribute on {label}: {raw}")
        if local(raw) not in supported:
            fail(f"unsupported attribute on {label}: {raw}")


def number(element: ET.Element, name: str, default: float, label: str) -> float:
    raw = element.get(f"{ANDROID}{name}")
    if raw is None:
        return default
    try:
        return float(raw)
    except ValueError:
        fail(f"{label}: android:{name} must be a number, got {raw!r}")


def format_number(value: float) -> str:
    """1.0 を 1 と出すなど、変形の記述を読みやすい表記に丸める。"""
    text = f"{value:.6f}".rstrip("0").rstrip(".")
    return text if text not in ("", "-0") else "0"


def group_transform(group: ET.Element, label: str) -> str | None:
    """`<group>` の変形を SVG の transform に翻訳する。

    Android の VGroup は行列を
    `T(pivot + translate) · R(rotation) · S(scale) · T(-pivot)` の順で合成する。
    SVG の transform リストは左から順に適用されるため、同じ並びで書けば一致する。
    """
    pivot_x = number(group, "pivotX", 0.0, label)
    pivot_y = number(group, "pivotY", 0.0, label)
    rotation = number(group, "rotation", 0.0, label)
    scale_x = number(group, "scaleX", 1.0, label)
    scale_y = number(group, "scaleY", 1.0, label)
    translate_x = number(group, "translateX", 0.0, label)
    translate_y = number(group, "translateY", 0.0, label)

    parts = []
    if (translate_x + pivot_x, translate_y + pivot_y) != (0.0, 0.0):
        parts.append(
            f"translate({format_number(translate_x + pivot_x)},{format_number(translate_y + pivot_y)})"
        )
    if rotation != 0.0:
        parts.append(f"rotate({format_number(rotation)})")
    if (scale_x, scale_y) != (1.0, 1.0):
        parts.append(f"scale({format_number(scale_x)},{format_number(scale_y)})")
    if (pivot_x, pivot_y) != (0.0, 0.0):
        parts.append(f"translate({format_number(-pivot_x)},{format_number(-pivot_y)})")

    return " ".join(parts) if parts else None


def convert_path(path: ET.Element, label: str) -> str:
    check_attrs(path, SUPPORTED_PATH_ATTRS, label)

    data = path.get(f"{ANDROID}pathData")
    if data is None:
        fail(f"{label}: <path> without android:pathData")

    attrs = []

    # SVG の fill の既定値は黒、Android の既定値は塗り無し。塗らないパスには
    # fill="none" を明示しないと黒く潰れる。
    fill = path.get(f"{ANDROID}fillColor")
    if fill is None:
        attrs.append('fill="none"')
    else:
        if not RGB.match(fill):
            fail(f"{label}: unsupported fillColor {fill!r}; only literal #RRGGBB is handled")
        attrs.append(f'fill="{fill}"')

    fill_type = path.get(f"{ANDROID}fillType")
    if fill_type is not None:
        if fill_type not in ("nonZero", "evenOdd"):
            fail(f"{label}: unknown fillType {fill_type!r}")
        attrs.append(f'fill-rule="{"evenodd" if fill_type == "evenOdd" else "nonzero"}"')

    stroke = path.get(f"{ANDROID}strokeColor")
    if stroke is not None:
        if not RGB.match(stroke):
            fail(f"{label}: unsupported strokeColor {stroke!r}; only literal #RRGGBB is handled")
        attrs.append(f'stroke="{stroke}"')
        attrs.append(f'stroke-width="{format_number(number(path, "strokeWidth", 0.0, label))}"')

        cap = path.get(f"{ANDROID}strokeLineCap")
        if cap is not None:
            if cap not in LINE_CAPS:
                fail(f"{label}: unknown strokeLineCap {cap!r}")
            attrs.append(f'stroke-linecap="{cap}"')

        join = path.get(f"{ANDROID}strokeLineJoin")
        if join is not None:
            if join not in LINE_JOINS:
                fail(f"{label}: unknown strokeLineJoin {join!r}")
            attrs.append(f'stroke-linejoin="{join}"')

        miter = path.get(f"{ANDROID}strokeMiterLimit")
        if miter is not None:
            attrs.append(f'stroke-miterlimit="{format_number(number(path, "strokeMiterLimit", 4.0, label))}"')
    elif fill is None:
        fail(f"{label}: <path> has neither android:fillColor nor android:strokeColor")

    attrs.append(f'd="{data}"')
    return "<path " + " ".join(attrs) + " />"


def convert_children(parent: ET.Element, source: Path, depth: int) -> list[str]:
    """`<vector>` / `<group>` の子要素を SVG の要素列に変換する。"""
    indent = "  " * depth
    lines = []

    for child in parent:
        tag = local(child.tag)
        if tag == "path":
            lines.append(indent + convert_path(child, f"{source.name} <path>"))
        elif tag == "group":
            label = f"{source.name} <group>"
            check_attrs(child, SUPPORTED_GROUP_ATTRS, label)
            transform = group_transform(child, label)
            open_tag = f'<g transform="{transform}">' if transform else "<g>"
            lines.append(indent + open_tag)
            lines.extend(convert_children(child, source, depth + 1))
            lines.append(indent + "</g>")
        else:
            fail(f"{source}: unsupported element <{tag}>; only <path> and <group> are handled")

    return lines


def parse_vector(path: Path) -> tuple[str, str, list[str]]:
    """Vector Drawable を (viewportWidth, viewportHeight, SVG 要素) に変換する。"""
    root = ET.parse(path).getroot()
    if local(root.tag) != "vector":
        fail(f"{path}: root element must be <vector>, got <{root.tag}>")
    check_attrs(root, SUPPORTED_VECTOR_ATTRS, f"{path.name} <vector>")

    width = root.get(f"{ANDROID}viewportWidth")
    height = root.get(f"{ANDROID}viewportHeight")
    if width is None or height is None:
        fail(f"{path}: <vector> must declare android:viewportWidth and android:viewportHeight")

    return width, height, convert_children(root, path, 1)


def parse_shape_color(path: Path) -> str:
    """単色の Shape Drawable（`<shape><solid>`）から塗り色を読み出す。"""
    root = ET.parse(path).getroot()
    if local(root.tag) != "shape":
        fail(f"{path}: root element must be <shape>, got <{root.tag}>")
    check_attrs(root, SUPPORTED_SHAPE_ATTRS, f"{path.name} <shape>")

    # Adaptive Icon の背景レイヤーはマスク前の 108dp キャンバス全体を覆う。矩形以外は
    # 全面塗りに落とせないため受け付けない。
    shape = root.get(f"{ANDROID}shape")
    if shape not in (None, "rectangle"):
        fail(f"{path}: unsupported android:shape {shape!r}; only 'rectangle' is handled")

    solids = [child for child in root if local(child.tag) == "solid"]
    others = [local(child.tag) for child in root if local(child.tag) != "solid"]
    if others:
        fail(f"{path}: unsupported element(s) {others}; only <solid> is handled")
    if len(solids) != 1:
        fail(f"{path}: <shape> must have exactly one <solid>, found {len(solids)}")
    check_attrs(solids[0], SUPPORTED_SOLID_ATTRS, f"{path.name} <solid>")

    color = solids[0].get(f"{ANDROID}color")
    if color is None:
        fail(f"{path}: <solid> without android:color")
    if not RGB.match(color):
        fail(f"{path}: unsupported solid color {color!r}; only literal #RRGGBB is handled")
    return color


def lookup_color(res: Path, name: str) -> str:
    """values/colors.xml から色リテラルを引く。"""
    for color in ET.parse(res / "values" / "colors.xml").getroot():
        if color.tag == "color" and color.get("name") == name:
            value = (color.text or "").strip()
            if not RGB.match(value):
                fail(f"@color/{name} is not a literal #RRGGBB color: {value!r}")
            return value
    fail(f"@color/{name} not found in values/colors.xml")


def layer_reference(icon: ET.Element, layer: str, source: str) -> str:
    elements = [child for child in icon if child.tag == layer]
    if len(elements) != 1:
        fail(f"{source}: <adaptive-icon> must have exactly one <{layer}>, found {len(elements)}")
    check_attrs(elements[0], SUPPORTED_LAYER_ATTRS, f"{source} <{layer}>")
    reference = elements[0].get(f"{ANDROID}drawable")
    if reference is None:
        fail(f"{source}: <{layer}> without android:drawable")
    return reference


def parse_adaptive_icon(path: Path) -> tuple[str, str]:
    root = ET.parse(path).getroot()
    if root.tag != "adaptive-icon":
        fail(f"{path}: root element must be <adaptive-icon>, got <{root.tag}>")
    return (
        layer_reference(root, "background", path.name),
        layer_reference(root, "foreground", path.name),
    )


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("usage: adaptive_icon_to_svg.py <res-dir>")
    res = Path(sys.argv[1])

    anydpi = res / "mipmap-anydpi-v26"
    background, foreground = parse_adaptive_icon(anydpi / "ic_launcher.xml")

    # 円形版は同じ SVG を円マスクでラスタライズしている。レイヤー参照が食い違うと
    # 図像が黙って食い違うので、ここで落とす。
    round_icon = anydpi / "ic_launcher_round.xml"
    if round_icon.is_file():
        if parse_adaptive_icon(round_icon) != (background, foreground):
            fail(
                "ic_launcher_round.xml references different layers than ic_launcher.xml; "
                "the round mipmap is rasterized from the same SVG"
            )

    if not foreground.startswith("@drawable/"):
        fail(f"unsupported <foreground> reference {foreground!r}; only @drawable/... is handled")
    width, height, body = parse_vector(res / "drawable" / f"{foreground.removeprefix('@drawable/')}.xml")

    if background.startswith("@color/"):
        color = lookup_color(res, background.removeprefix("@color/"))
        body.insert(0, f'  <rect x="0" y="0" width="{width}" height="{height}" fill="{color}" />')
    elif background.startswith("@drawable/"):
        bg_path = res / "drawable" / f"{background.removeprefix('@drawable/')}.xml"
        if local(ET.parse(bg_path).getroot().tag) == "shape":
            color = parse_shape_color(bg_path)
            body.insert(0, f'  <rect x="0" y="0" width="{width}" height="{height}" fill="{color}" />')
        else:
            bg_width, bg_height, bg_body = parse_vector(bg_path)
            if (bg_width, bg_height) != (width, height):
                fail(
                    f"viewport mismatch: foreground is {width}x{height} but "
                    f"{bg_path.name} is {bg_width}x{bg_height}"
                )
            body = bg_body + body
    else:
        fail(f"unsupported <background> reference {background!r}")

    print(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}">')
    print("\n".join(body))
    print("</svg>")


if __name__ == "__main__":
    main()
