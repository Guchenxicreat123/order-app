-- ============================================================
--  点餐小程序 · 数据库初始化脚本（由线上真实结构导出，17 张表）
--
--  ⚠️ 身份模型说明：
--    t_user 以 open_id（微信 openid）为**主键**，不存在自增 id。
--    各业务表的 user_id / owner_user_id / confirmed_by 均为 varchar(64)，
--    存的是 openid 字符串，不是数字。
--    openid 由微信服务端凭 code + AppID + AppSecret 换取，
--    同一用户在同一小程序下永久唯一，因此天然保证账号唯一性。
--
--  token_version：JWT 撤销版本号。签发时写入 claim，请求时与库中比对，
--    递增即可一键踢掉该用户全部已登录设备（无需黑名单表，零额外查询）。
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `family_id` bigint NOT NULL DEFAULT '0',
  `dish_id` bigint DEFAULT NULL,
  `dish_name` varchar(64) NOT NULL,
  `dish_emoji` varchar(8) DEFAULT '',
  `custom_ings` text,
  `spice_level` tinyint DEFAULT '0',
  `remark` varchar(255) DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_dish` (`dish_id`),
  KEY `idx_family_user` (`family_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `sort` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL,
  `image_emoji` varchar(8) DEFAULT '',
  `description` text,
  `spice_level` tinyint DEFAULT '0',
  `price` decimal(10,2) DEFAULT '0.00',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  -- 菜名唯一是「家庭内」维度：不同家庭可以存在同名菜（如各自从公共库加入"鱼香肉丝"）
  UNIQUE KEY `uk_family_name` (`family_id`, `name`),
  KEY `idx_cat` (`category_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_dish_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `dish_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_dish` (`user_id`,`dish_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_dish_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dish_id` bigint NOT NULL,
  `ing_id` bigint NOT NULL,
  `amount` decimal(10,3) NOT NULL,
  `unit` varchar(16) DEFAULT '',
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dish_ing` (`dish_id`,`ing_id`),
  KEY `idx_ing` (`ing_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_family` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `code` varchar(8) NOT NULL,
  `owner_user_id` varchar(64) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_owner` (`owner_user_id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_family_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `role` varchar(16) NOT NULL DEFAULT 'MEMBER',
  `nickname` varchar(64) DEFAULT NULL,
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_user` (`family_id`,`user_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_chef` (`family_id`,`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL,
  `unit` varchar(16) NOT NULL,
  `price` decimal(8,2) NOT NULL DEFAULT '0.00',
  `emoji` varchar(8) DEFAULT '',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_family` (`name`,`family_id`),
  KEY `idx_cat` (`category_id`),
  KEY `idx_name` (`name`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_ingredient_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `emoji` varchar(8) DEFAULT '',
  `sort` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_family` (`name`,`family_id`),
  KEY `idx_sort` (`sort`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_menu_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `family_id` bigint NOT NULL DEFAULT '0',
  `order_id` bigint DEFAULT NULL,
  `user_nickname` varchar(64) DEFAULT '',
  `dish_id` bigint DEFAULT NULL,
  `dish_name` varchar(64) NOT NULL,
  `dish_emoji` varchar(8) DEFAULT '',
  `custom_ings` text,
  `spice_level` tinyint DEFAULT '0',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00',
  `remark` varchar(255) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `confirmed_by` varchar(64) DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_status_date` (`user_id`,`status`,`created_at`),
  KEY `idx_family_date` (`family_id`,`created_at`),
  KEY `idx_created_date` ((cast(`created_at` as date)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_menu_item_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_item_id` bigint NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `score` tinyint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_user` (`menu_item_id`,`user_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_nickname` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `item_count` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `confirmed_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`,`created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `t_public_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `image_emoji` varchar(16) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `spice_level` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_public_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL DEFAULT '0',
  `unit` varchar(16) NOT NULL DEFAULT '克',
  `price` decimal(8,2) NOT NULL DEFAULT '0.00',
  `emoji` varchar(8) DEFAULT '',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cat` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_push_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `openid` varchar(64) NOT NULL,
  `menu_item_id` bigint DEFAULT NULL,
  `type` varchar(16) NOT NULL,
  `success` tinyint DEFAULT '0',
  `error_msg` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`created_at`),
  KEY `idx_success` (`success`,`created_at`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_push_pending` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `openid` varchar(64) NOT NULL,
  `template_id` varchar(64) NOT NULL,
  `payload` text NOT NULL,
  `menu_item_id` bigint DEFAULT NULL,
  `attempts` int DEFAULT '0',
  `next_retry_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '0',
  `last_error` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_pending` (`status`,`next_retry_at`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_user` (
  `open_id` varchar(64) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_chef` tinyint NOT NULL DEFAULT '0',
  `allow_push` tinyint NOT NULL DEFAULT '0',
  `chef_pin` varchar(32) DEFAULT '123456',
  `active_family_id` bigint DEFAULT NULL,
  `token_version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`open_id`),
  KEY `idx_openid` (`open_id`),
  KEY `idx_chef` (`is_chef`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 种子数据：菜品分类（业务基线，缺失会导致小程序首页为空）
-- ------------------------------------------------------------
INSERT INTO t_category (name, sort)
SELECT * FROM (
  SELECT '🍳 热菜' AS name, 1 AS sort UNION ALL
  SELECT '🥒 凉菜', 2 UNION ALL
  SELECT '🍚 主食', 3 UNION ALL
  SELECT '🥣 汤品', 4 UNION ALL
  SELECT '🥤 饮品', 5
) AS s
WHERE NOT EXISTS (SELECT 1 FROM t_category);
