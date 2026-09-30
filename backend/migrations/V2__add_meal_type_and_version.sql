-- ============================================
-- 改造迁移脚本 v2
-- ============================================
USE order_app;

-- 1. 商品表：加乐观锁版本号（如果还没有）
ALTER TABLE t_product ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

-- 2. 订单表：加就餐方式和地址
ALTER TABLE t_order ADD COLUMN IF NOT EXISTS meal_type VARCHAR(20) NOT NULL DEFAULT 'dine_in' COMMENT 'dine_in=堂食, takeout=自取, delivery=配送';
ALTER TABLE t_order ADD COLUMN IF NOT EXISTS address VARCHAR(255) DEFAULT NULL COMMENT '配送地址';

-- 3. 修复逻辑删除配置（去掉不存在的 deleted 字段引用）已在 application.yml 中移除

SELECT '✅ 迁移完成' AS msg;
