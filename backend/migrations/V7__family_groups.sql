-- V7__family_groups.sql
-- 家庭组改造：所有业务按 family_id 隔离

-- 1. 家庭表
CREATE TABLE IF NOT EXISTS t_family (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(64) NOT NULL,
    code          VARCHAR(8) NOT NULL UNIQUE COMMENT '6位加入码',
    owner_user_id BIGINT NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_user_id),
    INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '家庭组';

-- 2. 家庭成员表
CREATE TABLE IF NOT EXISTS t_family_member (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    role        VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER/CHEF/MEMBER',
    nickname    VARCHAR(64) NULL,
    joined_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_family_user (family_id, user_id),
    INDEX idx_user (user_id),
    INDEX idx_chef (family_id, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '家庭成员';

-- 3. t_user 加 active_family_id
ALTER TABLE t_user ADD COLUMN active_family_id BIGINT DEFAULT NULL COMMENT '当前操作的家庭';

-- 4. 业务表加 family_id 列（NULL 时视为 0，向后兼容旧数据；后续 TRUNCATE 后都是新数据）
ALTER TABLE t_menu_item ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0 AFTER user_id;
ALTER TABLE t_menu_item ADD INDEX idx_family_date (family_id, created_at);

ALTER TABLE t_cart ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0 AFTER user_id;
ALTER TABLE t_cart ADD INDEX idx_family_user (family_id, user_id);

ALTER TABLE t_dish ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_dish ADD INDEX idx_family (family_id);

ALTER TABLE t_dish_ingredient ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_dish_ingredient ADD INDEX idx_family (family_id);

ALTER TABLE t_dish_blacklist ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_dish_blacklist ADD INDEX idx_family (family_id);

ALTER TABLE t_ingredient_category ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_ingredient_category ADD INDEX idx_family (family_id);

ALTER TABLE t_ingredient ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_ingredient ADD INDEX idx_family (family_id);

ALTER TABLE t_menu_item_rating ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_menu_item_rating ADD INDEX idx_family (family_id);

ALTER TABLE t_push_log ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_push_log ADD INDEX idx_family (family_id);

ALTER TABLE t_push_pending ADD COLUMN family_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_push_pending ADD INDEX idx_family (family_id);

-- 5. 清空旧业务数据（保留 t_user）
TRUNCATE TABLE t_menu_item;
TRUNCATE TABLE t_cart;
TRUNCATE TABLE t_dish;
TRUNCATE TABLE t_dish_ingredient;
TRUNCATE TABLE t_dish_blacklist;
TRUNCATE TABLE t_ingredient_category;
TRUNCATE TABLE t_ingredient;
TRUNCATE TABLE t_menu_item_rating;
TRUNCATE TABLE t_push_log;
TRUNCATE TABLE t_push_pending;
-- t_user.t_category 是早前的分类表（v2 改造时已被 V3 重命名为 t_dish），先忽略

-- 6. 创建默认家庭
INSERT INTO t_family (name, code, owner_user_id)
SELECT '我的家', 'DEMO00', MIN(id) FROM t_user;

-- 7. 把所有用户都加入这个默认家庭（角色：创建者=OWNER，其他=MEMBER）
INSERT INTO t_family_member (family_id, user_id, role, nickname)
SELECT f.id, u.id,
       CASE WHEN u.id = f.owner_user_id THEN 'OWNER' ELSE 'MEMBER' END,
       u.nickname
FROM t_user u, t_family f
WHERE f.code = 'DEMO00'
ON DUPLICATE KEY UPDATE role = VALUES(role);

-- 8. 设置所有用户的 active_family_id 为默认家庭
UPDATE t_user SET active_family_id = (SELECT id FROM t_family WHERE code = 'DEMO00');