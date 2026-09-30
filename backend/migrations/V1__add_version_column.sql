-- 给商品表加乐观锁版本号
ALTER TABLE t_product ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
