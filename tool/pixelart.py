#!/usr/bin/env python3
"""16x16 像素画小工具。

用法：
    python pixelart.py whistle          # 生成 whistle.png + 预览
    python pixelart.py whistle --out foo.png
    python pixelart.py --list

画法：在 SPRITES 里加一条 16 行、每行 16 个字符的字符串。
'.' 是透明，其余字符查 PALETTE。改一个字符就是改一个像素。
"""

from pathlib import Path

from PIL import Image

SIZE = 16
SCALE = 16

# 常用色板；需要新颜色时在这里加字符
PALETTE = {
    ".": (0, 0, 0, 0),          # 透明
    "K": (48, 34, 18, 255),     # 深色描边
    "D": (122, 80, 20, 255),    # 暗部
    "M": (194, 138, 38, 255),   # 中间色
    "L": (242, 196, 88, 255),   # 亮部
    "H": (255, 244, 184, 255),  # 高光
    "W": (238, 238, 232, 255),  # 白
    "G": (104, 166, 88, 255),   # 绿
    "R": (184, 68, 58, 255),    # 红
    "B": (72, 108, 176, 255),   # 蓝
    "P": (124, 82, 142, 255),   # 紫
    "Y": (238, 202, 92, 255),   # 黄
}

SPRITES = {
    # 铜色哨子：圆哨体 + 右上吹嘴 + 顶部挂环 + 气孔
    "whistle": [
        ".....KK.........",
        "....H..K........",
        "....K..K........",
        "....K..K........",
        "....KKKK........",
        "......K.........",
        "...HHKLKKKKKKKK.",
        "...HLKKLMDHLLLLK",
        "..KLLKKLMDMMMMMK",
        "..KMMMMMMMMKKKK.",
        "..KMMMMMMMK.....",
        "...KDDDDDK......",
        "...KKDDDKK......",
        ".....KKK........",
        "................",
        "................",
    ],
}


def render(rows):
    if len(rows) != SIZE:
        raise ValueError(f"需要 {SIZE} 行，当前 {len(rows)} 行")
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = img.load()
    for y, row in enumerate(rows):
        if len(row) != SIZE:
            raise ValueError(f"第 {y} 行需要 {SIZE} 个字符，当前 {len(row)} 个")
        for x, char in enumerate(row):
            if char == ".":
                continue
            if char not in PALETTE:
                raise ValueError(f"未知字符 {char!r}（第 {y} 行第 {x} 列）")
            pixels[x, y] = PALETTE[char]
    return img


def main():
    import argparse

    parser = argparse.ArgumentParser(description="从字符网格生成 16x16 PNG")
    parser.add_argument("name", nargs="?", help="SPRITES 里的名字")
    parser.add_argument("--out", help="输出 PNG，默认 <name>.png")
    parser.add_argument("--preview-dir", default="_dsh-research", help="放大预览目录")
    parser.add_argument("--list", action="store_true", help="列出所有 sprite")
    args = parser.parse_args()

    if args.list or not args.name:
        print("sprites:", ", ".join(SPRITES))
        return

    if args.name not in SPRITES:
        raise SystemExit(f"没有 sprite: {args.name}，可选：{', '.join(SPRITES)}")

    img = render(SPRITES[args.name])
    out = Path(args.out or f"{args.name}.png")
    img.save(out)

    preview_dir = Path(args.preview_dir)
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview = preview_dir / f"{out.stem}_preview.png"
    img.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST).save(preview)
    print(f"wrote {out} ({SIZE}x{SIZE}) and {preview}")


if __name__ == "__main__":
    main()
