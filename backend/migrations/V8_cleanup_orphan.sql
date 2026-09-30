-- ============================================================
-- V8 预清理：删除孤儿数据（基于预检查结果）
-- ============================================================
-- 用途：清理所有指向不存在父表的孤儿行，让 V8__add_foreign_keys.sql 能成功执行
-- 决策原则：
--   - 订单归属 mock 用户/family：全部删（金额 0 或遗留测试数据）
--   - 家庭归属 mock 用户：全部删（整个家庭连带成员、订单都没意义）
--   - 配方引用已删配菜：删孤儿配方行（保留菜品本身）
--   - 用户的活跃家庭指向已删家庭：置 NULL（不删用户）
--
-- ⚠️ 此脚本会永久删除孤儿行；执行前确保已备份数据库
-- ============================================================

START TRANSACTION;

-- 1. 清掉指向已删配菜的孤儿配方行
DELETE FROM t_dish_ingredient
WHERE ing_id NOT IN (SELECT id FROM t_ingredient);

-- 2. 清掉归属 mock 用户的孤儿家庭（连带删成员、订单）
--    先找这些家庭的 id
DROP TEMPORARY TABLE IF EXISTS _orphan_families;
CREATE TEMPORARY TABLE _orphan_families AS
SELECT id FROM t_family WHERE owner_user_id NOT IN (SELECT open_id FROM t_user);

--    删这些家庭的订单
DELETE FROM t_order WHERE family_id IN (SELECT id FROM _orphan_families);

--    删这些家庭的成员
DELETE FROM t_family_member WHERE family_id IN (SELECT id FROM _orphan_families);

--    删家庭本身
DELETE FROM t_family WHERE id IN (SELECT id FROM _orphan_families);

-- 3. 修复孤儿订单：仍然 family/user 已删的（应该已经被上面清掉了，但保险起见再做一次）
DELETE FROM t_order
WHERE family_id NOT IN (SELECT id FROM t_family)
   OR user_id COLLATE utf8mb4_0900_ai_ci NOT IN (SELECT open_id FROM t_user);

-- 4. 把指向已删家庭的 active_family_id 置 NULL
UPDATE t_user SET active_family_id = NULL
WHERE active_family_id IS NOT NULL
  AND active_family_id NOT IN (SELECT id FROM t_family);

-- 5. 顺手清掉孤儿的菜单项/评分（理论上已经是 0，但保险）
DELETE FROM t_menu_item_rating
WHERE menu_item_id NOT IN (SELECT id FROM t_menu_item)
   OR user_id COLLATE utf8mb4_0900_ai_ci NOT IN (SELECT open_id FROM t_user);

COMMIT;

-- 输出清理后状态
SELECT 'cleanup done' AS status;
SELECT 'user count' AS k, COUNT(*) AS v FROM t_user
UNION ALL SELECT 'family count', COUNT(*) FROM t_family
UNION ALL SELECT 'order count', COUNT(*) FROM t_order
UNION ALL SELECT 'dish_ingredient count', COUNT(*) FROM t_dish_ingredient;