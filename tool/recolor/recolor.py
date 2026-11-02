import os
import numpy as np
from PIL import Image

# ================= 配置区 =================
TARGET_R = 255
TARGET_G = 255
TARGET_B = 255

BASE_LIGHTNESS = 0.85

# 对比度压缩 (0.0 ~ 1.0)。1.0 原始对比，越小越平。
# 0.75 保留云团层次，避免整片糊成高光。
CONTRAST = 0.75
# ==========================================

def recolor_images():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    count = 0

    for filename in os.listdir(current_dir):
        if filename.lower().endswith(".png"):
            filepath = os.path.join(current_dir, filename)
            try:
                img = Image.open(filepath).convert("RGBA")
                arr = np.array(img).astype(float)

                rgb = arr[:, :, :3]
                alpha = arr[:, :, 3]

                luminance = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
                luminance = luminance / 255.0

                # 对比度压缩：把亮度往 0.5 收紧
                luminance = 0.5 + (luminance - 0.5) * CONTRAST
                # 防止极端值越界（CONTRAST > 1 时可能超出 0~1）
                luminance = np.clip(luminance, 0.0, 1.0)

                factor = BASE_LIGHTNESS + (1.0 - BASE_LIGHTNESS) * luminance

                new_r = TARGET_R * factor
                new_g = TARGET_G * factor
                new_b = TARGET_B * factor

                new_arr = np.zeros_like(arr)
                new_arr[:, :, 0] = new_r
                new_arr[:, :, 1] = new_g
                new_arr[:, :, 2] = new_b
                new_arr[:, :, 3] = alpha

                result_img = Image.fromarray(new_arr.astype(np.uint8), mode="RGBA")
                result_img.save(filepath, "PNG")
                print(f"已转色: {filename}")
                count += 1

            except Exception as e:
                print(f"失败: {filename}, 错误: {e}")

    print(f"\n处理完成，共转换了 {count} 张图片。")

if __name__ == "__main__":
    recolor_images()