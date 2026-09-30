-- 公共配菜表
CREATE TABLE IF NOT EXISTS t_public_ingredient (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(64) NOT NULL,
  category_id bigint NOT NULL DEFAULT 0,
  unit varchar(16) NOT NULL DEFAULT '克',
  price decimal(8,2) NOT NULL DEFAULT 0.00,
  emoji varchar(8) DEFAULT '',
  status tinyint DEFAULT 1,
  created_at datetime DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cat (category_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共配菜库';

-- 公共配菜分类用 t_ingredient_category 的公共分类（family_id=0）
-- 先确保有公共分类
INSERT IGNORE INTO t_ingredient_category (name, emoji, sort, family_id) VALUES
('蔬菜', '🥬', 1, 0),
('蛋奶', '🥚', 2, 0),
('肉禽', '🥩', 3, 0),
('海鲜', '🦐', 4, 0),
('调料', '🧂', 5, 0),
('主食', '🍚', 6, 0),
('水果', '🍎', 7, 0);

-- 获取公共分类ID
SET @cat_veg = (SELECT id FROM t_ingredient_category WHERE name='蔬菜' AND family_id=0 LIMIT 1);
SET @cat_egg = (SELECT id FROM t_ingredient_category WHERE name='蛋奶' AND family_id=0 LIMIT 1);
SET @cat_meat = (SELECT id FROM t_ingredient_category WHERE name='肉禽' AND family_id=0 LIMIT 1);
SET @cat_sea = (SELECT id FROM t_ingredient_category WHERE name='海鲜' AND family_id=0 LIMIT 1);
SET @cat_cond = (SELECT id FROM t_ingredient_category WHERE name='调料' AND family_id=0 LIMIT 1);
SET @cat_staple = (SELECT id FROM t_ingredient_category WHERE name='主食' AND family_id=0 LIMIT 1);
SET @cat_fruit = (SELECT id FROM t_ingredient_category WHERE name='水果' AND family_id=0 LIMIT 1);

-- 插入公共配菜数据（常用配菜，供参考）
INSERT IGNORE INTO t_public_ingredient (name, category_id, unit, price, emoji, status) VALUES
-- 蔬菜
('土豆', @cat_veg, '斤', 3.00, '🥔', 1),
('白菜', @cat_veg, '斤', 2.00, '🥬', 1),
('番茄', @cat_veg, '斤', 8.00, '🍅', 1),
('黄瓜', @cat_veg, '斤', 4.00, '🥒', 1),
('胡萝卜', @cat_veg, '斤', 3.00, '🥕', 1),
('洋葱', @cat_veg, '斤', 3.00, '🧅', 1),
('青椒', @cat_veg, '斤', 5.00, '🫑', 1),
('茄子', @cat_veg, '斤', 4.00, '🍆', 1),
('蘑菇', @cat_veg, '斤', 12.00, '🍄', 1),
('菠菜', @cat_veg, '把', 4.00, '🥬', 1),
('生菜', @cat_veg, '把', 5.00, '🥬', 1),
('西兰花', @cat_veg, '斤', 10.00, '🥦', 1),
-- 蛋奶
('鸡蛋', @cat_egg, '个', 1.50, '🥚', 1),
('豆腐', @cat_egg, '块', 4.00, '🍲', 1),
('牛奶', @cat_egg, '瓶', 15.00, '🥛', 1),
('酸奶', @cat_egg, '瓶', 8.00, '🥛', 1),
('奶酪', @cat_egg, '块', 25.00, '🧀', 1),
-- 肉禽
('猪肉', @cat_meat, '斤', 25.00, '🥩', 1),
('鸡肉', @cat_meat, '斤', 22.00, '🍗', 1),
('排骨', @cat_meat, '斤', 40.00, '🍖', 1),
('牛肉', @cat_meat, '斤', 45.00, '🥩', 1),
('五花肉', @cat_meat, '斤', 28.00, '🥩', 1),
('羊肉', @cat_meat, '斤', 50.00, '🥩', 1),
-- 海鲜
('虾', @cat_sea, '斤', 60.00, '🦐', 1),
('鱼', @cat_sea, '斤', 35.00, '🐟', 1),
('鱿鱼', @cat_sea, '斤', 50.00, '🦑', 1),
('螃蟹', @cat_sea, '只', 80.00, '🦀', 1),
-- 调料
('盐', @cat_cond, '勺', 0.50, '🧂', 1),
('酱油', @cat_cond, '勺', 0.80, '🥫', 1),
('醋', @cat_cond, '勺', 1.00, '🧂', 1),
('糖', @cat_cond, '勺', 1.00, '🍬', 1),
('葱', @cat_cond, '把', 2.00, '🧅', 1),
('姜', @cat_cond, '块', 2.00, '🧅', 1),
('蒜', @cat_cond, '头', 3.00, '🧄', 1),
('辣椒', @cat_cond, '个', 8.00, '🌶️', 1),
-- 主食
('米', @cat_staple, '斤', 6.00, '🍚', 1),
('面条', @cat_staple, '斤', 8.00, '🍜', 1),
('面粉', @cat_staple, '斤', 5.00, '🌾', 1),
-- 水果
('苹果', @cat_fruit, '个', 6.00, '🍎', 1),
('香蕉', @cat_fruit, '根', 4.00, '🍌', 1),
('橙子', @cat_fruit, '个', 3.00, '🍊', 1),
('西瓜', @cat_fruit, '斤', 5.00, '🍉', 1);

SELECT CONCAT('公共配菜总数: ', COUNT(*)) as info FROM t_public_ingredient;
