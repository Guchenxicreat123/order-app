-- ============================================================
-- V10：20 道家常菜的常用配方（公共菜 ↔ 公共配菜）
-- 配比基于"3~4 人份"日常用量，可被各家庭微调
-- 幂等：使用 ON DUPLICATE KEY UPDATE，可重复执行
-- ============================================================
-- 配菜 id 速查（与 seed_public_library.sql 对齐）：
--   蔬菜：1白菜 2土豆 3番茄 4黄瓜 5青椒 6胡萝卜 7茄子 8韭菜 9菠菜
--        10葱 11姜 12蒜 13洋葱 14豆芽 15芹菜
--   蛋奶豆制品：16鸡蛋 17豆腐 18嫩豆腐 19豆腐皮 20豆浆 21牛奶
--   肉禽：22猪肉 23五花肉 24排骨 25里脊 26鸡腿 27鸡翅 28鸡胸肉 29牛肉 30羊肉 31肉末
--   海鲜：32虾 33鱼 34鱿鱼 35蟹
--   主食：36大米 37面粉 38面条 39饺子皮 40糯米粉 41馒头
--   调料：42盐 43酱油 44醋 45糖 46料酒 47生抽 48老抽 49蚝油 50花椒 51干辣椒 52八角 53香叶 54油
--   水果：55苹果 56香蕉 57橙子 58西瓜 59葡萄
-- ============================================================

SET NAMES utf8mb4;

-- 1. 番茄炒蛋（id=1）— 经典家常
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (1, 3,   2,    '个'),  -- 番茄 2 个
  (1, 16,  3,    '个'),  -- 鸡蛋 3 个
  (1, 10,  1,    '根'),  -- 葱 1 根
  (1, 42,  1,    '勺'),  -- 盐 1 勺
  (1, 47,  1,    '勺'),  -- 生抽 1 勺
  (1, 45,  1,    '勺'),  -- 糖 1 勺（提鲜）
  (1, 54,  2,    '勺')   -- 油 2 勺
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 2. 青椒炒肉（id=2）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (2, 22,  200,  '克'),  -- 猪肉 200g
  (2, 5,   3,    '个'),  -- 青椒 3 个
  (2, 12,  3,    '颗'),  -- 蒜 3 颗
  (2, 11,  10,   '克'),  -- 姜 10g
  (2, 10,  1,    '根'),  -- 葱 1 根
  (2, 47,  1,    '勺'),  -- 生抽
  (2, 46,  1,    '勺'),  -- 料酒
  (2, 42,  1,    '勺'),  -- 盐
  (2, 54,  2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 3. 土豆炖牛肉（id=3）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (3, 29,  300,  '克'),  -- 牛肉 300g
  (3, 2,   2,    '个'),  -- 土豆 2 个（约 400g）
  (3, 13,  1,    '个'),  -- 洋葱 1 个
  (3, 11,  15,   '克'),  -- 姜
  (3, 52,  2,    '颗'),  -- 八角
  (3, 47,  2,    '勺'),  -- 生抽
  (3, 48,  1,    '勺'),  -- 老抽（上色）
  (3, 46,  1,    '勺'),  -- 料酒
  (3, 42,  1,    '勺'),  -- 盐
  (3, 54,  2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 4. 红烧肉（id=4）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (4, 23,  500,  '克'),  -- 五花肉 500g
  (4, 45,  30,   '克'),  -- 糖 30g（炒糖色）
  (4, 47,  3,    '勺'),  -- 生抽
  (4, 48,  2,    '勺'),  -- 老抽
  (4, 46,  2,    '勺'),  -- 料酒
  (4, 11,  20,   '克'),  -- 姜
  (4, 52,  2,    '颗'),  -- 八角
  (4, 53,  3,    '片'),  -- 香叶
  (4, 42,  1,    '勺'),  -- 盐
  (4, 54,  2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 5. 蒜蓉西兰花（注：项目里没有"西兰花"配菜，用相近的"白菜"代替；可在后台改为正式名）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (5, 1,   300,  '克'),  -- 白菜 300g（暂代西兰花，建议管理后台把名字改对）
  (5, 12,  5,    '颗'),  -- 蒜 5 颗（"蒜蓉"主角）
  (5, 42,  1,    '勺'),  -- 盐
  (5, 47,  1,    '勺'),  -- 生抽
  (5, 54,  2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 6. 麻婆豆腐（id=6）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (6, 17,  1,    '块'),  -- 豆腐 1 块（约 400g）
  (6, 31,  100,  '克'),  -- 肉末 100g
  (6, 51,  5,    '个'),  -- 干辣椒 5 个
  (6, 50,  1,    '勺'),  -- 花椒
  (6, 12,  3,    '颗'),  -- 蒜
  (6, 11,  10,   '克'),  -- 姜
  (6, 10,  1,    '根'),  -- 葱
  (6, 43,  2,    '勺'),  -- 酱油
  (6, 49,  1,    '勺'),  -- 蚝油
  (6, 42,  1,    '勺'),  -- 盐
  (6, 54,  3,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 7. 可乐鸡翅（id=7）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (7, 27,  10,   '个'),  -- 鸡翅 10 个
  (7, 43,  2,    '勺'),  -- 酱油
  (7, 11,  10,   '克'),  -- 姜
  (7, 10,  1,    '根'),  -- 葱
  (7, 46,  1,    '勺'),  -- 料酒
  (7, 42,  1,    '勺'),  -- 盐
  (7, 54,  1,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 8. 糖醋里脊（id=8）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (8, 25,  300,  '克'),  -- 里脊 300g
  (8, 37,  50,   '克'),  -- 面粉（挂糊）
  (8, 45,  30,   '克'),  -- 糖
  (8, 44,  2,    '勺'),  -- 醋
  (8, 47,  1,    '勺'),  -- 生抽
  (8, 16,  1,    '个'),  -- 鸡蛋（挂糊用）
  (8, 42,  1,    '勺'),  -- 盐
  (8, 54,  5,    '勺')   -- 油（炸）
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 9. 回锅肉（id=9）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (9, 23,  300,  '克'),  -- 五花肉 300g
  (9, 5,   2,    '个'),  -- 青椒 2 个
  (9, 8,   50,   '克'),  -- 韭菜 50g（暂代"蒜苗"）
  (9, 12,  3,    '颗'),  -- 蒜
  (9, 11,  10,   '克'),  -- 姜
  (9, 43,  2,    '勺'),  -- 酱油
  (9, 45,  1,    '勺'),  -- 糖
  (9, 46,  1,    '勺'),  -- 料酒
  (9, 42,  1,    '勺'),  -- 盐
  (9, 54,  2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 10. 清蒸鲈鱼（注：用"鱼"代替鲈鱼）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (10, 33, 1,    '条'),  -- 鱼 1 条
  (10, 10, 2,    '根'),  -- 葱
  (10, 11, 20,   '克'),  -- 姜
  (10, 47, 2,    '勺'),  -- 生抽
  (10, 54, 1,    '勺'),  -- 油
  (10, 42, 1,    '勺')   -- 盐
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 11. 虾仁炒蛋（id=11）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (11, 32, 200,  '克'),  -- 虾 200g（去壳即虾仁）
  (11, 16, 3,    '个'),  -- 鸡蛋 3 个
  (11, 10, 1,    '根'),  -- 葱
  (11, 42, 1,    '勺'),  -- 盐
  (11, 46, 1,    '勺'),  -- 料酒
  (11, 54, 2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 12. 地三鲜（id=12）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (12, 7,  2,    '根'),  -- 茄子 2 根
  (12, 2,  1,    '个'),  -- 土豆 1 个
  (12, 5,  2,    '个'),  -- 青椒 2 个
  (12, 12, 3,    '颗'),  -- 蒜
  (12, 11, 10,   '克'),  -- 姜
  (12, 47, 2,    '勺'),  -- 生抽
  (12, 45, 1,    '勺'),  -- 糖
  (12, 42, 1,    '勺'),  -- 盐
  (12, 54, 3,    '勺')   -- 油（茄子吸油）
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 13. 凉拌黄瓜（id=13）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (13, 4,  2,    '根'),  -- 黄瓜 2 根
  (13, 12, 4,    '颗'),  -- 蒜 4 颗
  (13, 44, 2,    '勺'),  -- 醋
  (13, 47, 1,    '勺'),  -- 生抽
  (13, 51, 2,    '个'),  -- 干辣椒
  (13, 54, 1,    '勺')   -- 油（泼辣油）
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 14. 凉拌豆腐皮（id=14）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (14, 19, 3,    '张'),  -- 豆腐皮 3 张
  (14, 10, 1,    '根'),  -- 葱
  (14, 12, 3,    '颗'),  -- 蒜
  (14, 44, 1,    '勺'),  -- 醋
  (14, 47, 1,    '勺'),  -- 生抽
  (14, 51, 2,    '个'),  -- 干辣椒
  (14, 54, 1,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 15. 凉拌木耳（注：项目里没有木耳配菜，跳过这道菜的配方，留给后台手工配）
-- 留着空白

-- 16. 蛋炒饭（id=16）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (16, 36, 300,  '克'),  -- 大米（已煮好的饭）300g
  (16, 16, 2,    '个'),  -- 鸡蛋 2 个
  (16, 10, 1,    '根'),  -- 葱
  (16, 42, 1,    '勺'),  -- 盐
  (16, 47, 1,    '勺'),  -- 生抽
  (16, 54, 2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 17. 番茄鸡蛋面（id=17）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (17, 38, 2,    '把'),  -- 面条 2 把
  (17, 3,  2,    '个'),  -- 番茄 2 个
  (17, 16, 2,    '个'),  -- 鸡蛋 2 个
  (17, 10, 1,    '根'),  -- 葱
  (17, 42, 1,    '勺'),  -- 盐
  (17, 47, 1,    '勺'),  -- 生抽
  (17, 54, 2,    '勺')   -- 油
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 18. 饺子（id=18）— 注：项目里有饺子皮
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (18, 39, 50,   '张'),  -- 饺子皮 50 张
  (18, 22, 300,  '克'),  -- 猪肉 300g（肥瘦比 3:7）
  (18, 8,  50,   '克'),  -- 韭菜 50g（经典搭配）
  (18, 16, 1,    '个'),  -- 鸡蛋 1 个
  (18, 11, 10,   '克'),  -- 姜
  (18, 42, 1,    '勺'),  -- 盐
  (18, 47, 2,    '勺'),  -- 生抽
  (18, 46, 1,    '勺'),  -- 料酒
  (18, 44, 1,    '勺')   -- 醋（蘸料）
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 19. 紫菜蛋花汤（注：项目里没有紫菜，跳过主料；用鸡蛋+葱做底）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (19, 16, 2,    '个'),  -- 鸡蛋 2 个
  (19, 10, 1,    '根'),  -- 葱
  (19, 42, 1,    '勺'),  -- 盐
  (19, 47, 1,    '勺')   -- 生抽
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);

-- 20. 排骨玉米汤（注：项目里没有玉米配菜，跳过）
INSERT INTO t_public_dish_ingredient (public_dish_id, ing_id, amount, unit) VALUES
  (20, 24, 500,  '克'),  -- 排骨 500g
  (20, 11, 20,   '克'),  -- 姜
  (20, 10, 2,    '根'),  -- 葱
  (20, 42, 1,    '勺'),  -- 盐
  (20, 46, 1,    '勺')   -- 料酒
ON DUPLICATE KEY UPDATE amount = VALUES(amount), unit = VALUES(unit);