-- ============================================
-- V3: 家庭点餐 V2.2 schema + 数据迁移
-- ============================================
USE order_app;

-- ========== 1. 旧表 DROP（V2 不再用商家接单）==========
DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_admin;

-- ========== 2. t_user 加字段 ==========
ALTER TABLE t_user
    ADD COLUMN is_chef TINYINT NOT NULL DEFAULT 0 COMMENT '0=普通 1=主厨',
    ADD COLUMN allow_push TINYINT NOT NULL DEFAULT 0 COMMENT '推送订阅授权',
    ADD INDEX idx_chef (is_chef);

-- 历史 V1 admin 用户（如果有）改为主厨；新主厨通过 /api/me/chef 认领
-- 这里没有 V1 admin 数据可迁（V1 admin 是独立 t_admin 表已 DROP），主厨需手动认领

-- ========== 3. 配菜分类（t_ingredient_category）==========
DROP TABLE IF EXISTS t_ingredient_category;
CREATE TABLE t_ingredient_category (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(32) NOT NULL,
    emoji      VARCHAR(8) DEFAULT '',
    sort       INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    INDEX idx_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '配菜分类';

-- 预置7 类
INSERT INTO t_ingredient_category (name, emoji, sort) VALUES
('蔬菜',  '🥬', 1),
('蛋奶',  '🥚', 2),
('肉禽',  '🥩', 3),
('海鲜',  '🦐', 4),
('调料',  '🧂', 5),
('主食',  '🍚', 6),
('水果',  '🍎', 7);

-- ========== 4. 配菜库（t_ingredient）==========
DROP TABLE IF EXISTS t_ingredient;
CREATE TABLE t_ingredient (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL,
    category_id BIGINT NOT NULL,
    unit       VARCHAR(16) NOT NULL COMMENT '克/个/根/把/块/勺/颗',
    price      DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '¥/单位',
    emoji      VARCHAR(8) DEFAULT '',
    status     TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    INDEX idx_cat (category_id),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '配菜';

-- 预置一些常用配菜（后续主厨在管理端补全）
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
-- 蔬菜
('土豆',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克', 6.00, '🥔'),
('白菜',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克', 3.00, '🥬'),
('番茄',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克', 8.00, '🍅'),
('西兰花', (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克', 10.00, '🥦'),
-- 蛋奶
('鸡蛋',  (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '个', 1.50, '🥚'),
('豆腐',  (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '块', 4.00, '🧈'),
('牛奶',  (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '瓶', 15.00, '🥛'),
-- 肉禽
('猪肉',  (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 30.00, '🥩'),
('鸡肉',  (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 25.00, '🍗'),
('排骨',  (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 40.00, '🍖'),
-- 海鲜
('虾',    (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 50.00, '🦐'),
('鱼',    (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 35.00, '🐟'),
-- 调料
('盐',    (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺', 0.50, '🧂'),
('酱油',  (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺', 0.80, '🥫'),
('油',    (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺', 1.00, '🛢️'),
-- 主食
('米',    (SELECT id FROM t_ingredient_category WHERE name='主食'), '克', 6.00, '🍚'),
('面条',  (SELECT id FROM t_ingredient_category WHERE name='主食'), '克', 8.00, '🍜'),
-- 水果
('苹果',  (SELECT id FROM t_ingredient_category WHERE name='水果'), '个', 5.00, '🍎'),
('香蕉',  (SELECT id FROM t_ingredient_category WHERE name='水果'), '根', 3.00, '🍌');

-- ========== 5. 固定菜（t_dish）==========
DROP TABLE IF EXISTS t_dish;
CREATE TABLE t_dish (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(64) NOT NULL,
    category_id  BIGINT NOT NULL,
    image_emoji  VARCHAR(8) DEFAULT '',
    description  TEXT DEFAULT NULL COMMENT '步骤/做法/备注 (Markdown)',
    spice_level  TINYINT DEFAULT 0 COMMENT '0=免辣 1=微辣 2=重辣',
    price        DECIMAL(10,2) DEFAULT 0 COMMENT '冗余 = 服务层算',
    status       TINYINT DEFAULT 1,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    INDEX idx_cat (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '固定菜谱';

-- ========== 6. 固定菜-配菜关联表（t_dish_ingredient）==========
DROP TABLE IF EXISTS t_dish_ingredient;
CREATE TABLE t_dish_ingredient (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    dish_id BIGINT NOT NULL,
    ing_id  BIGINT NOT NULL,
    amount  DECIMAL(10,3) NOT NULL COMMENT '数值类型，避免精度丢失',
    unit    VARCHAR(16) DEFAULT '' COMMENT '冗余显示',
    UNIQUE KEY uk_dish_ing (dish_id, ing_id),
    INDEX idx_ing (ing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '菜谱-配菜关联';

-- ========== 7. 今日菜单条目（t_menu_item）==========
DROP TABLE IF EXISTS t_menu_item;
CREATE TABLE t_menu_item (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    user_nickname   VARCHAR(64) DEFAULT '' COMMENT '冗余：下单人昵称',
    dish_id         BIGINT DEFAULT NULL COMMENT 'NULL=自定义菜',
    dish_name       VARCHAR(64) NOT NULL,
    dish_emoji      VARCHAR(8) DEFAULT '',
    custom_ings     TEXT DEFAULT NULL COMMENT '自定义菜的配料 JSON',
    spice_level     TINYINT DEFAULT 0,
    price           DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '后端实时算',
    remark          VARCHAR(255) DEFAULT NULL,
    status          TINYINT DEFAULT 0 COMMENT '-1=已撤销 0=已下单 1=已确认',
    confirmed_by    BIGINT DEFAULT NULL COMMENT '主厨 user_id',
    confirmed_at    DATETIME DEFAULT NULL,
    version         INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_user_status_date (user_id, status, created_at),
    INDEX idx_created_date ((DATE(created_at)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '今日菜单项';

-- ========== 8. 推送日志（t_push_log）==========
DROP TABLE IF EXISTS t_push_log;
CREATE TABLE t_push_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    openid        VARCHAR(64) NOT NULL,
    menu_item_id  BIGINT DEFAULT NULL,
    type          VARCHAR(16) NOT NULL COMMENT 'NEW_ORDER / STATUS_CHANGED',
    success       TINYINT DEFAULT 0,
    error_msg     VARCHAR(255) DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_success (success, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '推送日志';

-- ========== 9. 推送重试队列（t_push_pending）==========
DROP TABLE IF EXISTS t_push_pending;
CREATE TABLE t_push_pending (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    openid        VARCHAR(64) NOT NULL,
    template_id   VARCHAR(64) NOT NULL,
    payload       TEXT NOT NULL COMMENT 'JSON 模板数据',
    menu_item_id  BIGINT DEFAULT NULL,
    attempts      INT DEFAULT 0,
    next_retry_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    status        TINYINT DEFAULT 0 COMMENT '0=pending 1=done 2=abandoned',
    last_error    VARCHAR(255) DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pending (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '推送重试队列';

-- ========== 10. 黑名单（t_dish_blacklist）==========
DROP TABLE IF EXISTS t_dish_blacklist;
CREATE TABLE t_dish_blacklist (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    dish_id    BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_dish (user_id, dish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '黑名单';

-- ========== 11. 评分（t_menu_item_rating）==========
DROP TABLE IF EXISTS t_menu_item_rating;
CREATE TABLE t_menu_item_rating (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    menu_item_id  BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    score         TINYINT NOT NULL COMMENT '1-5 星',
    comment       VARCHAR(255) DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_menu_user (menu_item_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '评分';

-- ========== 12. 保留 t_category 但 TRUNCATE + 重新 INSERT 预置分类 ==========
TRUNCATE TABLE t_category;
INSERT INTO t_category (name, sort, status) VALUES
('🍳 热菜', 1, 1),
('🥒 凉菜', 2, 1),
('🍚 主食', 3, 1),
('🥣 汤品', 4, 1),
('🥤 饮品', 5, 1);

SELECT '✅ V3 migration 完成' AS msg;
