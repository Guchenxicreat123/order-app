-- ============================================================
-- 迁移：t_user.id (bigint 主键) → open_id (varchar 主键)
-- 所有引用 user 的列 bigint → varchar(64)，并把数字 id 映射成 open_id 字符串
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 子表 user_id / owner_user_id / confirmed_by：bigint → varchar(64)
ALTER TABLE t_cart MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_dish_blacklist MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_family MODIFY owner_user_id varchar(64) NOT NULL;
ALTER TABLE t_family_member MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_menu_item MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_menu_item MODIFY confirmed_by varchar(64) NULL;
ALTER TABLE t_menu_item_rating MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_order MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_order MODIFY confirmed_by varchar(64) NULL;
ALTER TABLE t_push_log MODIFY user_id varchar(64) NOT NULL;
ALTER TABLE t_push_pending MODIFY user_id varchar(64) NOT NULL;

-- 2) 把数字 id 替换成 open_id 字符串
UPDATE t_cart t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_dish_blacklist t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_family t JOIN t_user u ON CAST(t.owner_user_id AS UNSIGNED) = u.id SET t.owner_user_id = u.open_id;
UPDATE t_family_member t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_menu_item t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_menu_item t JOIN t_user u ON CAST(t.confirmed_by AS UNSIGNED) = u.id SET t.confirmed_by = u.open_id WHERE t.confirmed_by IS NOT NULL;
UPDATE t_menu_item_rating t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_order t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_order t JOIN t_user u ON CAST(t.confirmed_by AS UNSIGNED) = u.id SET t.confirmed_by = u.open_id WHERE t.confirmed_by IS NOT NULL;
UPDATE t_push_log t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;
UPDATE t_push_pending t JOIN t_user u ON CAST(t.user_id AS UNSIGNED) = u.id SET t.user_id = u.open_id;

-- 3) t_user：删除 id 主键，open_id 升为主键
ALTER TABLE t_user DROP PRIMARY KEY;
ALTER TABLE t_user DROP INDEX open_id;
ALTER TABLE t_user MODIFY open_id varchar(64) NOT NULL FIRST;
ALTER TABLE t_user ADD PRIMARY KEY (open_id);
ALTER TABLE t_user DROP COLUMN id;

SET FOREIGN_KEY_CHECKS = 1;

-- 验证
SELECT 't_user 主键=' AS info, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='order_app' AND TABLE_NAME='t_user' AND COLUMN_KEY='PRI';
SELECT 't_family_member sample' AS info, user_id FROM t_family_member LIMIT 3;
SELECT 't_order sample' AS info, user_id FROM t_order LIMIT 3;
SELECT '总用户数' AS info, COUNT(*) FROM t_user;
