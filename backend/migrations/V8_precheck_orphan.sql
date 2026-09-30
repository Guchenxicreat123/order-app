-- ============================================================
-- V8 预检查：找出孤儿数据（必须在 V8__add_foreign_keys.sql 之前清理）
-- ============================================================
-- 运行方式：mysql -uroot -p order_app < V8__precheck_orphan.sql
-- 每一行输出：表名.列名孤儿数
-- 如果输出全部为 0，可以直接跑 V8__add_foreign_keys.sql
-- 如果有 > 0，需要人工评估：要么补数据，要么删孤儿行
-- ============================================================

SELECT 't_dish_ingredient.dish_id 孤儿' AS src, COUNT(*) AS n FROM t_dish_ingredient d LEFT JOIN t_dish x ON d.dish_id = x.id WHERE x.id IS NULL
UNION ALL
SELECT 't_dish_ingredient.ing_id 孤儿', COUNT(*) FROM t_dish_ingredient d LEFT JOIN t_ingredient x ON d.ing_id = x.id WHERE x.id IS NULL
UNION ALL
SELECT 't_menu_item.user_id 孤儿', COUNT(*) FROM t_menu_item m LEFT JOIN t_user u ON m.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_menu_item.dish_id 孤儿', COUNT(*) FROM t_menu_item m LEFT JOIN t_dish d ON m.dish_id = d.id WHERE m.dish_id IS NOT NULL AND d.id IS NULL
UNION ALL
SELECT 't_cart.user_id 孤儿', COUNT(*) FROM t_cart c LEFT JOIN t_user u ON c.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_cart.dish_id 孤儿', COUNT(*) FROM t_cart c LEFT JOIN t_dish d ON c.dish_id = d.id WHERE c.dish_id IS NOT NULL AND d.id IS NULL
UNION ALL
SELECT 't_menu_item_rating.menu_item_id 孤儿', COUNT(*) FROM t_menu_item_rating r LEFT JOIN t_menu_item m ON r.menu_item_id = m.id WHERE m.id IS NULL
UNION ALL
SELECT 't_menu_item_rating.user_id 孤儿', COUNT(*) FROM t_menu_item_rating r LEFT JOIN t_user u ON r.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_family_member.family_id 孤儿', COUNT(*) FROM t_family_member fm LEFT JOIN t_family f ON fm.family_id = f.id WHERE f.id IS NULL
UNION ALL
SELECT 't_family_member.user_id 孤儿', COUNT(*) FROM t_family_member fm LEFT JOIN t_user u ON fm.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_family.owner_user_id 孤儿', COUNT(*) FROM t_family f LEFT JOIN t_user u ON f.owner_user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_dish_blacklist.user_id 孤儿', COUNT(*) FROM t_dish_blacklist b LEFT JOIN t_user u ON b.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_dish_blacklist.dish_id 孤儿', COUNT(*) FROM t_dish_blacklist b LEFT JOIN t_dish d ON b.dish_id = d.id WHERE d.id IS NULL
UNION ALL
SELECT 't_push_log.user_id 孤儿', COUNT(*) FROM t_push_log l LEFT JOIN t_user u ON l.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_push_pending.user_id 孤儿', COUNT(*) FROM t_push_pending p LEFT JOIN t_user u ON p.user_id = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_order.family_id 孤儿', COUNT(*) FROM t_order o LEFT JOIN t_family f ON o.family_id = f.id WHERE f.id IS NULL
UNION ALL
SELECT 't_order.user_id 孤儿', COUNT(*) FROM t_order o LEFT JOIN t_user u ON o.user_id COLLATE utf8mb4_0900_ai_ci = u.open_id WHERE u.open_id IS NULL
UNION ALL
SELECT 't_dish.category_id 孤儿', COUNT(*) FROM t_dish d LEFT JOIN t_category c ON d.category_id = c.id WHERE c.id IS NULL
UNION ALL
SELECT 't_ingredient.category_id 孤儿', COUNT(*) FROM t_ingredient i LEFT JOIN t_ingredient_category ic ON i.category_id = ic.id WHERE ic.id IS NULL
UNION ALL
SELECT 't_user.active_family_id 孤儿', COUNT(*) FROM t_user u LEFT JOIN t_family f ON u.active_family_id = f.id WHERE u.active_family_id IS NOT NULL AND f.id IS NULL
;