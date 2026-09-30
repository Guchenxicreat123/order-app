-- ============================================
-- V11: 订阅消息推送完善
--   1. t_push_log 增加 payload 列：记录真正发出去的模板数据，方便后台排查
--   2. t_push_pending 增加 push_type 列：重试时才知道该用哪个模板（NEW_ORDER / STATUS_CHANGED）
-- 注意：本项目没有引入 Flyway，migrations 目录是手工执行的。
--       重复执行本文件会因为列已存在而报 1060，属于预期。
-- ============================================
USE order_app;

ALTER TABLE t_push_log
    ADD COLUMN payload TEXT NULL COMMENT '实际发送的模板数据 JSON';

ALTER TABLE t_push_pending
    ADD COLUMN push_type VARCHAR(16) NOT NULL DEFAULT 'NEW_ORDER' COMMENT 'NEW_ORDER / STATUS_CHANGED',
    ADD INDEX idx_status_type (status, push_type);
