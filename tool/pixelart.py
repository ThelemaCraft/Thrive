#!/usr/bin/env python3
"""16x16 像素画小工具。

用法：
    python pixelart.py          # 为 SPRITES 里每个 key 生成 <key>.png + 预览

画法：在 SPRITES 里加一条 16 行、每行 16 个字符的字符串。
'.' 是透明，其余字符查 PALETTE。改一个字符就是改一个像素。

输出位置：全部落在本脚本所在目录，与运行时 CWD 无关。
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

    # ── 云 ──
    "w": (252, 253, 255, 255),  # 云白（主体）
    "c": (232, 238, 250, 255),  # 云色（浅影）
    "s": (206, 216, 234, 255),  # 云影（深部）
}

SPRITES = {
    
    "whirlwind_sword": [
            "................",
            ".............KKK",
            "............Kwwc",
            "...........Kwwc.",
            "..........Kwwcs.",
            ".........Kwscs..",
            "........Kwwcs...",
            ".......Kwscs....",
            "......Kwwcs.....",
            ".....Kwscs......",
            "....Kwwcs.......",
            "...MYYYc........",
            "....KDK.........",
            "....KDK.........",
            "....KDK.........",
            "................",
        ]
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
    script_dir = Path(__file__).resolve().parent

    for name, rows in SPRITES.items():
        img = render(rows)

        out = script_dir / f"{name}.png"
        img.save(out)

        preview = script_dir / f"{name}_preview.png"
        img.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST).save(preview)

        print(f"wrote {out}")
        print(f"wrote {preview}")


if __name__ == "__main__":
    main()