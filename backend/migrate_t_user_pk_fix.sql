-- 修正 t_user：先去 auto_increment，再换主键
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE t_user MODIFY id bigint NOT NULL;
ALTER TABLE t_user DROP PRIMARY KEY;
ALTER TABLE t_user DROP INDEX open_id;
ALTER TABLE t_user MODIFY open_id varchar(64) NOT NULL FIRST;
ALTER TABLE t_user ADD PRIMARY KEY (open_id);
ALTER TABLE t_user DROP COLUMN id;
SET FOREIGN_KEY_CHECKS = 1;

SELECT 't_user 结构' AS info;
DESCRIBE t_user;
SELECT 't_user 主键' AS info, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='order_app' AND TABLE_NAME='t_user' AND COLUMN_KEY='PRI';
SELECT 't_family_member user_id 样本' AS info, user_id FROM t_family_member LIMIT 3;
SELECT 't_order user_id 样本' AS info, user_id FROM t_order LIMIT 3;
SELECT '用户数' AS info, COUNT(*) FROM t_user;
