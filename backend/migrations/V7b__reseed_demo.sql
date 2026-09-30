-- V7b__reseed_demo.sql
-- 为默认家庭重新初始化：配菜分类 + 配菜库 + 默认菜谱

SET @family_id = (SELECT id FROM t_family WHERE code = 'DEMO00');

-- ========== 配菜分类 ==========
INSERT INTO t_ingredient_category (family_id, name, emoji, sort) VALUES
(@family_id, '蔬菜',  '🥬', 1),
(@family_id, '蛋奶',  '🥚', 2),
(@family_id, '肉禽',  '🥩', 3),
(@family_id, '海鲜',  '🦐', 4),
(@family_id, '调料',  '🧂', 5),
(@family_id, '主食',  '🍚', 6),
(@family_id, '水果',  '🍎', 7);

-- ========== 配菜库（基础19种 + V5 扩充的常用食材）==========
-- 蔬菜
INSERT INTO t_ingredient (family_id, name, category_id, unit, price, emoji) VALUES
(@family_id, '土豆',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蔬菜'), '克', 6.00, '🥔'),
(@family_id, '白菜',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蔬菜'), '克', 3.00, '🥬'),
(@family_id, '番茄',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蔬菜'), '克', 8.00, '🍅'),
(@family_id, '西兰花', (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蔬菜'), '克', 10.00, '🥦'),
-- 蛋奶
(@family_id, '鸡蛋',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蛋奶'), '个', 1.50, '🥚'),
(@family_id, '豆腐',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蛋奶'), '块', 4.00, '🧈'),
(@family_id, '牛奶',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='蛋奶'), '瓶', 15.00, '🥛'),
-- 肉禽
(@family_id, '猪肉',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='肉禽'), '克', 30.00, '🥩'),
(@family_id, '鸡肉',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='肉禽'), '克', 25.00, '🍗'),
(@family_id, '排骨',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='肉禽'), '克', 40.00, '🍖'),
-- 海鲜
(@family_id, '虾',     (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='海鲜'), '克', 50.00, '🦐'),
(@family_id, '鱼',     (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='海鲜'), '克', 35.00, '🐟'),
-- 调料
(@family_id, '盐',     (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='调料'), '勺', 0.50, '🧂'),
(@family_id, '酱油',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='调料'), '勺', 0.80, '🥫'),
(@family_id, '油',     (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='调料'), '勺', 1.00, '🛢️'),
-- 主食
(@family_id, '米',     (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='主食'), '克', 6.00, '🍚'),
(@family_id, '面条',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='主食'), '克', 8.00, '🍜'),
-- 水果
(@family_id, '苹果',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='水果'), '个', 5.00, '🍎'),
(@family_id, '香蕉',   (SELECT id FROM t_ingredient_category WHERE family_id=@family_id AND name='水果'), '根', 3.00, '🍌');

-- ========== 菜品分类（t_category，V2 改造时保留）==========
TRUNCATE TABLE t_category;
INSERT INTO t_category (name, sort, status) VALUES
('🍳 热菜', 1, 1),
('🥒 凉菜', 2, 1),
('🍚 主食', 3, 1),
('🥣 汤品', 4, 1),
('🥤 饮品', 5, 1);

-- ========== 默认菜谱（4 道）==========
INSERT INTO t_dish (family_id, name, category_id, image_emoji, description, spice_level, status) VALUES
(@family_id, '番茄炒蛋', (SELECT id FROM t_category WHERE name='🍳 热菜'), '🍳', '番茄切块，鸡蛋打散，热油下锅翻炒', 0, 1),
(@family_id, '麻婆豆腐', (SELECT id FROM t_category WHERE name='🍳 热菜'), '🌶️', '豆腐切块焯水，豆瓣酱炒香，小火慢炖', 2, 1),
(@family_id, '宫保鸡丁', (SELECT id FROM t_category WHERE name='🍳 热菜'), '🍗', '鸡丁腌制，花生米炒香，酱汁勾芡', 1, 1),
(@family_id, '清蒸鱼',   (SELECT id FROM t_category WHERE name='🥣 汤品'), '🐟', '鱼处理干净，葱姜铺底，大火蒸10分钟', 0, 1);

-- 番茄炒蛋的配菜
INSERT INTO t_dish_ingredient (family_id, dish_id, ing_id, amount, unit) VALUES
(@family_id, (SELECT id FROM t_dish WHERE family_id=@family_id AND name='番茄炒蛋'),
 (SELECT id FROM t_ingredient WHERE family_id=@family_id AND name='番茄'), 200, '克'),
(@family_id, (SELECT id FROM t_dish WHERE family_id=@family_id AND name='番茄炒蛋'),
 (SELECT id FROM t_ingredient WHERE family_id=@family_id AND name='鸡蛋'), 3, '个'),
(@family_id, (SELECT id FROM t_dish WHERE family_id=@family_id AND name='番茄炒蛋'),
 (SELECT id FROM t_ingredient WHERE family_id=@family_id AND name='盐'), 1, '勺'),
(@family_id, (SELECT id FROM t_dish WHERE family_id=@family_id AND name='番茄炒蛋'),
 (SELECT id FROM t_ingredient WHERE family_id=@family_id AND name='油'), 2, '勺');

SELECT '✅ V7b reseed 完成' AS msg;