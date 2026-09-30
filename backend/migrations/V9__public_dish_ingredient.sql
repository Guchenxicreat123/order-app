-- ============================================================
-- V9：公共菜配方表（公共菜 → 公共配菜）
-- 作用：让"番茄炒蛋需要 2 个番茄 + 3 个鸡蛋"这类信息结构化存储
--      可以基于 t_public_ingredient 的 price 自动计算成本
-- 关系：
--   t_public_dish (1) ←→ (N) t_public_dish_ingredient
--   t_public_dish_ingredient.ing_id → t_public_ingredient.id
-- 幂等：使用 IF NOT EXISTS，可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_public_dish_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_dish_id` bigint NOT NULL,
  `ing_id` bigint NOT NULL,
  `amount` decimal(10,3) NOT NULL,
  `unit` varchar(16) NOT NULL DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pdi_dish_ing` (`public_dish_id`, `ing_id`),
  KEY `idx_pdi_ing` (`ing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 加外键约束（已经存在的表跳过）
SET @fk1 = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = 'order_app'
              AND TABLE_NAME = 't_public_dish_ingredient'
              AND CONSTRAINT_NAME = 'fk_pdi_dish');
SET @sql = IF(@fk1 = 0,
  'ALTER TABLE t_public_dish_ingredient
   ADD CONSTRAINT fk_pdi_dish FOREIGN KEY (public_dish_id) REFERENCES t_public_dish(id) ON DELETE CASCADE',
  'SELECT "fk_pdi_dish already exists" AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk2 = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = 'order_app'
              AND TABLE_NAME = 't_public_dish_ingredient'
              AND CONSTRAINT_NAME = 'fk_pdi_ing');
SET @sql = IF(@fk2 = 0,
  'ALTER TABLE t_public_dish_ingredient
   ADD CONSTRAINT fk_pdi_ing FOREIGN KEY (ing_id) REFERENCES t_public_ingredient(id) ON DELETE RESTRICT',
  'SELECT "fk_pdi_ing already exists" AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;