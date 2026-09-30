-- ============================================================
-- V8 外键补打（collation 修正后剩余部分）
-- ============================================================
-- 适用场景：第一次跑 V8__add_foreign_keys.sql 时被 collation 不兼容中断，
-- 已成功部分留下；此脚本只补打剩余外键。
-- ============================================================

-- 9. 订单 → 家庭 + 用户
ALTER TABLE t_order
  ADD CONSTRAINT fk_o_fam  FOREIGN KEY (family_id) REFERENCES t_family(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_o_user FOREIGN KEY (user_id)   REFERENCES t_user(open_id) ON DELETE RESTRICT;

-- 10. 菜品 → 分类
ALTER TABLE t_dish
  ADD CONSTRAINT fk_dish_cat FOREIGN KEY (category_id) REFERENCES t_category(id) ON DELETE RESTRICT;

-- 11. 配菜 → 配菜分类
ALTER TABLE t_ingredient
  ADD CONSTRAINT fk_ing_cat FOREIGN KEY (category_id) REFERENCES t_ingredient_category(id) ON DELETE RESTRICT;

-- 12. 用户活跃家庭
ALTER TABLE t_user
  ADD CONSTRAINT fk_u_fam FOREIGN KEY (active_family_id) REFERENCES t_family(id) ON DELETE SET NULL;