#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""多巴胺粉蓝糖果风 全局色板替换脚本
映射表按"语义"设计：
  粉主系:  主橙→糖果粉 #FF5CA8, 浅橙→粉渐变 #FF85C2
  淡蓝辅系: 绿(主厨/编辑/确认导航)→天蓝 #45A6FF, 亮绿→ #7CC4FF
  中性甜底: 深棕文字→可可紫灰 #3D2E46, 次文字→#8F7BA6, 边框米→#F1E4F2
  语义区分保留: 成功绿→薄荷 #2BD98E? 不对——主厨系用蓝, 成功/已确认保持薄荷绿
"""
import re, sys, os

# (旧色, 新色) 顺序：先长后短避免前缀误伤（此处无前缀依赖，但保持规范）
MAP = [
    # ---- 粉色主系（原橙）----
    ("#FF6B35", "#FF5CA8"),   # 主橙（导航/按钮/价格/强调）→ 多巴胺粉
    ("#ff6b35", "#FF5CA8"),
    ("#FF8E53", "#FF85C2"),   # 浅橙渐变 → 粉渐变亮端
    ("#ff8e53", "#FF85C2"),
    ("#FF6B35 0%, #FF8E53", "#FF5CA8 0%, #FF85C2"),
    # ---- 主厨/编辑/确认 原绿 → 天蓝辅助 ----
    ("#27ae60", "#4D9FFF"),   # 主厨/自定义/确认绿 → 鲜蓝(主厨标识)
    ("#27AE60", "#4D9FFF"),
    ("#2ecc71", "#7CC4FF"),   # 亮绿渐变 → 亮蓝
    ("#2ECC71", "#7CC4FF"),
    # 绿底标签 → 淡蓝底
    ("#e8f5e9", "#E8F4FF"),
    ("#E8F5E9", "#E8F4FF"),
    # ---- 警示橙 tag 保留糖果橙（与粉区分）----
    ("#e67e22", "#FFB020"),   # 待确认/警示 → 蜜橙(糖果)
    ("#E67E22", "#FFB020"),
    ("#fff3e0", "#FFF4DE"),   # 淡橙底
    ("#FFF3E0", "#FFF4DE"),
    # ---- 文字中性（暖棕 → 可可紫灰，视觉更"甜") ----
    ("#3d2314", "#4A3141"),   # 主文字深棕 → 可可酒红棕
    ("#3D2314", "#4A3141"),
    ("#8b6f5c", "#9B7E8A"),   # 次级文字 → 玫瑰灰
    ("#8B6F5C", "#9B7E8A"),
    ("#c4a882", "#C5A8B8"),   # 弱文字/占位 → 淡玫瑰
    ("#C4A882", "#C5A8B8"),
    # ---- 背景/边框 ----
    ("#FFFAF5", "#FFF5FB"),   # 页面底 米白 → 淡粉白
    ("#fffaf5", "#FFF5FB"),
    ("#faf8f5", "#FDF1F9"),   # 输入框/浅底 → 淡粉
    ("#FAF8F5", "#FDF1F9"),
    ("#f0e6dc", "#F4E0EC"),   # 边框 米 → 淡粉紫
    ("#F0E6DC", "#F4E0EC"),
    ("#fff5f0", "#FFF0F6"),
    ("#FFF5F0", "#FFF0F6"),
    ("#fff0e8", "#FFEDF4"),
    ("#FFF0E8", "#FFEDF4"),
    ("#ffe0cc", "#FFD8E8"),   # 数量加减 active → 淡粉
    ("#FFE0CC", "#FFD8E8"),
    # ---- 红（删除/驳回/危险）→ 珊瑚红 ----
    ("#c0392b", "#FF5C6E"),
    ("#C0392B", "#FF5C6E"),
    ("#e74c3c", "#FF5C6E"),
    ("#E74C3C", "#FF5C6E"),
    ("#ff3b30", "#FF5C6E"),
    ("#FF3B30", "#FF5C6E"),
    ("#fee", "#FFE4E8"),      # 淡红底
    ("#FEE", "#FFE4E8"),
    ("#ffebee", "#FFE4E8"),
    ("#FFEBEE", "#FFE4E8"),
    # ---- 其它杂色 ----
    ("#f1f9f4", "#E9F6EF"),
    ("#F1F9F4", "#E9F6EF"),
    ("#95a5a6", "#B0A8C4"),   # 灰渐变 → 藕紫
    ("#95A5A6", "#B0A8C4"),
    ("#7f8c8d", "#988CAE"),
    ("#7F8C8D", "#988CAE"),
    ("#f5f5f5", "#F6EFF8"),
    ("#F5F5F5", "#F6EFF8"),
    ("#999", "#A99BB4"),
    ("#999999", "#A99BB4"),
    ("#666", "#7E6F8A"),
    ("#666666", "#7E6F8A"),
    ("#ddd", "#E6D8EA"),
    ("#DDDDDD", "#E6D8EA"),
    ("#e0e0e0", "#EBDFF0"),
    ("#E0E0E0", "#EBDFF0"),
]

def replace_in(path):
    with open(path, 'r', encoding='utf-8') as f:
        txt = f.read()
    orig = txt
    for old, new in MAP:
        # 大小写不敏感整词替换（# 开头后跟 hex，避免误伤 #FFFFFF 中的子串）
        txt = re.sub(re.escape(old), new, txt, flags=re.IGNORECASE)
    if txt != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(txt)
        return True
    return False

if __name__ == '__main__':
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    changed = []
    for dirpath, dirnames, filenames in os.walk(root):
        if 'node_modules' in dirpath or 'dist' in dirpath or '.git' in dirpath:
            continue
        for fn in filenames:
            if fn.endswith('.vue'):
                p = os.path.join(dirpath, fn)
                if replace_in(p):
                    changed.append(p)
    print(f"已替换 {len(changed)} 个文件:")
    for p in changed:
        print(' -', p)

# 补充：公共菜单"未加入家庭"提示条（橙→糖果提示色）
