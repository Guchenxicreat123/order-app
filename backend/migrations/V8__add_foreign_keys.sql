-- ============================================================
-- V8: 为关键业务表补外键约束
-- ============================================================
-- ⚠️ 运行前必须先执行 V8__precheck_orphan.sql 找出孤儿数据并清理
-- 否则 ALTER TABLE 会因违反外键约束而失败。
--
-- 删除策略约定：
--   - 业务明细（订单明细/购物车/评分）→ CASCADE 跟随父表
--   - 配置类（菜品/配菜/分类）→ RESTRICT，阻止误删有引用的行
-- ============================================================

-- 1. 菜品配方 → 菜品/配菜（CASCADE 菜品；RESTRICT 配菜，需先清空引用）
ALTER TABLE t_dish_ingredient
  ADD CONSTRAINT fk_di_dish FOREIGN KEY (dish_id) REFERENCES t_dish(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_di_ing  FOREIGN KEY (ing_id)  REFERENCES t_ingredient(id) ON DELETE RESTRICT;

-- 2. 菜单项 → 用户 + 菜品（CASCADE 用户：用户注销后菜单项随之消失；RESTRICT 菜品：有订单引用的菜品不让删）
ALTER TABLE t_menu_item
  ADD CONSTRAINT fk_mi_user FOREIGN KEY (user_id) REFERENCES t_user(open_id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_mi_dish FOREIGN KEY (dish_id) REFERENCES t_dish(id) ON DELETE RESTRICT;

-- 3. 购物车 → 用户 + 菜品
ALTER TABLE t_cart
  ADD CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES t_user(open_id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_cart_dish FOREIGN KEY (dish_id) REFERENCES t_dish(id) ON DELETE RESTRICT;

-- 4. 评分 → 菜单项 + 用户
ALTER TABLE t_menu_item_rating
  ADD CONSTRAINT fk_mir_menu FOREIGN KEY (menu_item_id) REFERENCES t_menu_item(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_mir_user FOREIGN KEY (user_id)        REFERENCES t_user(open_id) ON DELETE CASCADE;

-- 5. 家庭成员 → 家庭 + 用户
ALTER TABLE t_family_member
  ADD CONSTRAINT fk_fm_fam  FOREIGN KEY (family_id) REFERENCES t_family(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_fm_user FOREIGN KEY (user_id)   REFERENCES t_user(open_id) ON DELETE CASCADE;

-- 6. 家庭主表 → 创建者
ALTER TABLE t_family
  ADD CONSTRAINT fk_fam_owner FOREIGN KEY (owner_user_id) REFERENCES t_user(open_id) ON DELETE RESTRICT;

-- 7. 黑名单 → 用户 + 菜品
ALTER TABLE t_dish_blacklist
  ADD CONSTRAINT fk_dbl_user FOREIGN KEY (user_id) REFERENCES t_user(open_id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_dbl_dish FOREIGN KEY (dish_id) REFERENCES t_dish(id) ON DELETE CASCADE;

-- 8. 推送日志/待发送 → 用户
ALTER TABLE t_push_log
  ADD CONSTRAINT fk_pl_user FOREIGN KEY (user_id) REFERENCES t_user(open_id) ON DELETE CASCADE;
ALTER TABLE t_push_pending
  ADD CONSTRAINT fk_pp_user FOREIGN KEY (user_id) REFERENCES t_user(open_id) ON DELETE CASCADE;

-- 9. 订单 → 家庭 + 用户
ALTER TABLE t_order
  ADD CONSTRAINT fk_o_fam  FOREIGN KEY (family_id) REFERENCES t_family(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_o_user FOREIGN KEY (user_id)   REFERENCES t_user(open_id) ON DELETE RESTRICT;

-- 10. 菜品 → 分类（菜品分类不允许删除有引用的分类）
ALTER TABLE t_dish
  ADD CONSTRAINT fk_dish_cat FOREIGN KEY (category_id) REFERENCES t_category(id) ON DELETE RESTRICT;

-- 11. 配菜 → 分类 + 配菜分类
ALTER TABLE t_ingredient
  ADD CONSTRAINT fk_ing_cat FOREIGN KEY (category_id) REFERENCES t_ingredient_category(id) ON DELETE RESTRICT;

-- 12. 用户活跃家庭（弱约束，避免删除家庭时牵连）
ALTER TABLE t_user
  ADD CONSTRAINT fk_u_fam FOREIGN KEY (active_family_id) REFERENCES t_family(id) ON DELETE SET NULL;