-- ============================================================
-- 后台管理员账号表（独立于小程序用户表 t_user）
-- 服务启动时会自动执行同样的建表语句，本文件用于人工迁移/审阅。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_admin_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(64)  DEFAULT NULL,
  remark        VARCHAR(255) DEFAULT NULL,
  last_login_at DATETIME     DEFAULT NULL,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员账号（独立于小程序用户）';

-- 默认账号 admin / 123456 由服务首次启动时用 bcrypt 写入（避免在 SQL 里固化哈希）。
