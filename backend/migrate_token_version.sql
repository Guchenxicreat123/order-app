-- ============================================================
-- 迁移：为 t_user 增加 token_version（JWT 撤销机制）
-- 每次签发 token 时把当前版本号写进 claim；
-- 请求校验时比对 DB 版本，不一致即视为已撤销（401）。
-- 递增该字段即可「一键踢掉该用户所有已登录设备」。
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE t_user
  ADD COLUMN token_version INT NOT NULL DEFAULT 0
  COMMENT 'JWT 版本号：递增即撤销该用户全部已签发 token'
  AFTER active_family_id;

-- 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'token_version';
