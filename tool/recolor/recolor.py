import os
import numpy as np
from PIL import Image

# ================= 配置区 =================
# 目标颜色 (R, G, B) - 默认是果汁的亮黄色 0xFFFF33
TARGET_R = 255
TARGET_G = 255
TARGET_B = 51

# 基础亮度补偿 (0.0 ~ 1.0)。防止原图极暗的地方变成死黑，0.2 表示最暗的地方也有 20% 的目标颜色。
BASE_LIGHTNESS = 0.2
# ==========================================

def recolor_images():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    count = 0
    
    for filename in os.listdir(current_dir):
        if filename.lower().endswith(".png"):
            filepath = os.path.join(current_dir, filename)
            try:
                # 转为 numpy 数组处理
                img = Image.open(filepath).convert("RGBA")
                arr = np.array(img).astype(float)
                
                # 分离 RGB 和 Alpha
                rgb = arr[:, :, :3]
                alpha = arr[:, :, 3]
                
                # 计算明暗（Luminance），使用国际标准的感知亮度权重
                # L = 0.299R + 0.587G + 0.114B
                luminance = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
                # 归一化到 0~1
                luminance = luminance / 255.0
                
                # 亮度映射补偿：即使原图是纯黑，也保留基础亮度
                factor = BASE_LIGHTNESS + (1.0 - BASE_LIGHTNESS) * luminance
                
                # 生成新的 RGB 颜色
                new_r = TARGET_R * factor
                new_g = TARGET_G * factor
                new_b = TARGET_B * factor
                
                # 组装新数组
                new_arr = np.zeros_like(arr)
                new_arr[:, :, 0] = new_r
                new_arr[:, :, 1] = new_g
                new_arr[:, :, 2] = new_b
                new_arr[:, :, 3] = alpha  # Alpha 通道原封不动保留
                
                # 保存覆盖
                result_img = Image.fromarray(new_arr.astype(np.uint8), mode="RGBA")
                result_img.save(filepath, "PNG")
                print(f"已转色: {filename}")
                count += 1
                
            except Exception as e:
                print(f"失败: {filename}, 错误: {e}")
                
    print(f"\n处理完成，共转换了 {count} 张图片。")

if __name__ == "__main__":
    recolor_images()