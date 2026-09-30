-- V6__cart_table.sql
-- 购物车表：用户暂存待下单的菜品，下单成功后删除

CREATE TABLE t_cart (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL COMMENT '加购用户',
    dish_id      BIGINT DEFAULT NULL COMMENT '固定菜ID，NULL=自定义菜',
    dish_name    VARCHAR(64) NOT NULL,
    dish_emoji   VARCHAR(8) DEFAULT '',
    custom_ings  TEXT DEFAULT NULL COMMENT '自定义菜配料 JSON',
    spice_level  TINYINT DEFAULT 0,
    remark       VARCHAR(255) DEFAULT NULL,
    price        DECIMAL(10,2) NOT NULL COMMENT '后端实时算',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_dish (dish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '购物车';