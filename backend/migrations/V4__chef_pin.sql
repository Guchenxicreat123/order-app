-- V4: 主厨登录 PIN
ALTER TABLE t_user ADD COLUMN chef_pin VARCHAR(32) DEFAULT '123456';

-- 为现有主厨设置默认 PIN
UPDATE t_user SET chef_pin = '123456' WHERE is_chef = 1;
