import os
from PIL import Image

def convert_to_white():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    
    for filename in os.listdir(current_dir):
        if filename.lower().endswith(".png"):
            filepath = os.path.join(current_dir, filename)
            try:
                img = Image.open(filepath).convert("RGBA")
                # 提取透明通道（决定形状和透明度）
                a = img.getchannel("A")
                # 生成纯白 RGB 通道
                white = Image.new("L", img.size, 255)
                # 合并成纯白+原透明度
                result = Image.merge("RGBA", (white, white, white, a))
                result.save(filepath, "PNG")
                print(f"已处理: {filename}")
            except Exception as e:
                print(f"失败: {filename}, {e}")

if __name__ == "__main__":
    convert_to_white()