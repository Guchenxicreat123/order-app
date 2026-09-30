package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.Dish;
import com.example.order.entity.DishIngredient;
import com.example.order.entity.FamilyMember;
import com.example.order.entity.Ingredient;
import com.example.order.mapper.DishIngredientMapper;
import com.example.order.mapper.DishMapper;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.mapper.IngredientMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DishService {

    /** 默认分类 ID（菜品未指定分类时兜底） */
    private static final Long DEFAULT_CATEGORY_ID = 1L;

    @Autowired private DishMapper dishMapper;
    @Autowired private DishIngredientMapper diMapper;
    @Autowired private IngredientMapper ingMapper;
    @Autowired private FamilyMemberMapper familyMemberMapper;

    /** 校验当前用户是否是指定家庭的 OWNER 或 CHEF */
    private Result<?> checkFamilyChefOrOwner(Long familyId) {
        if (familyId == null) return Result.error(400, "请先选择家庭");
        String userId = UserContext.get();
        FamilyMember me = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "您不是该家庭成员");
        if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
            return Result.error(403, "只有创建者或主厨才能管理家庭菜单");
        }
        return Result.ok();
    }

    // ---- 价格自动计算：sum(配菜单价 × 用量) ----
    public BigDecimal computeDishPrice(Long dishId) {
        List<DishIngredient> dis = diMapper.selectList(
                new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, dishId));
        if (dis.isEmpty()) return BigDecimal.ZERO;

        Set<Long> ids = dis.stream().map(DishIngredient::getIngId).collect(Collectors.toSet());
        List<Ingredient> ingredients = ingMapper.selectBatchIds(ids);
        Map<Long, Ingredient> map = ingredients.stream()
                .collect(Collectors.toMap(Ingredient::getId, i -> i));

        BigDecimal total = BigDecimal.ZERO;
        for (DishIngredient di : dis) {
            Ingredient ing = map.get(di.getIngId());
            if (ing != null) {
                total = total.add(ing.getPrice().multiply(di.getAmount()));
            }
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ---- 菜谱列表（公开）----
    public Result<List<Dish>> list() {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        List<Dish> dishes = dishMapper.selectList(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getFamilyId, familyId)
                        .eq(Dish::getStatus, 1)
                        .orderByAsc(Dish::getCategoryId)
                        .orderByAsc(Dish::getId));
        // 补齐冗余价格
        for (Dish d : dishes) {
            if (d.getPrice() == null || d.getPrice().compareTo(BigDecimal.ZERO) == 0) {
                d.setPrice(computeDishPrice(d.getId()));
                // 更新冗余字段
                dishMapper.update(null,
                        new LambdaUpdateWrapper<Dish>()
                                .eq(Dish::getId, d.getId())
                                .set(Dish::getPrice, d.getPrice()));
            }
        }
        return Result.ok(dishes);
    }

    // ---- 菜谱详情（含配菜列表）----
    public Result<Map<String, Object>> getDetail(Long id) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        Dish dish = dishMapper.selectById(id);
        if (dish == null) return Result.error(404, "菜谱不存在");
        if (familyId != null && dish.getFamilyId() != null && !dish.getFamilyId().equals(familyId)) {
            return Result.error(403, "无权访问其他家庭菜谱");
        }

        List<DishIngredient> dis = diMapper.selectList(
                new LambdaQueryWrapper<DishIngredient>()
                        .eq(DishIngredient::getDishId, id));
        if (dis.isEmpty()) {
            return Result.ok(Map.of("dish", dish, "ingredients", List.of()));
        }

        List<Ingredient> ingredients = ingMapper.selectBatchIds(
                dis.stream().map(DishIngredient::getIngId).collect(Collectors.toSet()));
        Map<Long, Ingredient> map = ingredients.stream()
                .collect(Collectors.toMap(Ingredient::getId, i -> i));

        List<Map<String, Object>> items = dis.stream().map(di -> {
            Ingredient ing = map.get(di.getIngId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ingId", di.getIngId());
            item.put("name", ing != null ? ing.getName() : "?");
            item.put("amount", di.getAmount());
            item.put("unit", di.getUnit());
            item.put("price", ing != null ? ing.getPrice() : BigDecimal.ZERO);
            item.put("emoji", ing != null ? ing.getEmoji() : "");
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dish", dish);
        result.put("ingredients", items);
        return Result.ok(result);
    }

    // ---- 主厨管理端列表（含所有状态）----
    public Result<List<Dish>> listAll() {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        return Result.ok(dishMapper.selectList(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getFamilyId, familyId)
                        .orderByAsc(Dish::getCategoryId)
                        .orderByAsc(Dish::getId)));
    }

    /**
     * 小程序端：当前家庭完整菜品列表（含已下架），要求当前用户是该家庭 OWNER/CHEF。
     * 每个菜附带 ingredients 配方明细（批量查询，避免 N+1）。
     */
    public Result<List<Map<String, Object>>> listAllForCurrentFamilyWithIngredients() {
        Long familyId = UserContext.getFamily();
        String userId = UserContext.get();
        if (familyId == null || userId == null) return Result.error(401, "请先登录");
        FamilyMember me = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "您不是该家庭成员");
        // 只有 OWNER / CHEF 才能进入编辑
        if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
            return Result.error(403, "只有创建者或主厨才能编辑");
        }

        List<Dish> dishes = dishMapper.selectList(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getFamilyId, familyId)
                        .orderByAsc(Dish::getCategoryId)
                        .orderByAsc(Dish::getId));
        return Result.ok(decorateWithIngredients(dishes));
    }

    /**
     * 给菜品列表批量附加 ingredients（含名称/用量/单位/单价/emoji）。
     * 一次查出全部关联 + 全部配菜，避免逐菜查询。
     */
    private List<Map<String, Object>> decorateWithIngredients(List<Dish> dishes) {
        if (dishes.isEmpty()) return new ArrayList<>();
        List<Long> dishIds = dishes.stream().map(Dish::getId).collect(Collectors.toList());

        // 查出所有关联行
        List<DishIngredient> rels = diMapper.selectList(
                new LambdaQueryWrapper<DishIngredient>().in(DishIngredient::getDishId, dishIds));
        if (rels.isEmpty()) {
            return dishes.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dish", d);
                m.put("ingredients", List.of());
                return m;
            }).collect(Collectors.toList());
        }

        // 查出所有涉及的配菜
        Set<Long> ingIds = rels.stream().map(DishIngredient::getIngId).collect(Collectors.toSet());
        Map<Long, Ingredient> ingMap = ingMapper.selectBatchIds(ingIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, i -> i));

        // 按 dishId 分组组装
        Map<Long, List<Map<String, Object>>> byDish = new LinkedHashMap<>();
        for (DishIngredient di : rels) {
            Ingredient ing = ingMap.get(di.getIngId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ingId", di.getIngId());
            item.put("name", ing != null ? ing.getName() : "?");
            item.put("amount", di.getAmount());
            item.put("unit", di.getUnit());
            item.put("price", ing != null ? ing.getPrice() : BigDecimal.ZERO);
            item.put("emoji", ing != null ? ing.getEmoji() : "");
            byDish.computeIfAbsent(di.getDishId(), k -> new ArrayList<>()).add(item);
        }

        return dishes.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dish", d);
            m.put("ingredients", byDish.getOrDefault(d.getId(), List.of()));
            return m;
        }).collect(Collectors.toList());
    }

    // ---- 创建菜谱 ----
    @Transactional
    public Result<Dish> create(Dish dish, List<Map<String, Object>> ingredients) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        Result<?> check = checkFamilyChefOrOwner(familyId);
        if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());
        dish.setFamilyId(familyId);
        // 计算价格
        if (ingredients != null && !ingredients.isEmpty()) {
            dish.setPrice(computeFromList(ingredients));
        }
        dishMapper.insert(dish);
        saveIngredients(dish.getId(), familyId, ingredients);
        return Result.ok(dish);
    }

    // ---- 更新菜谱 ----
    @Transactional
    public Result<Void> update(Long id, Dish dish, List<Map<String, Object>> ingredients) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        Result<?> check = checkFamilyChefOrOwner(familyId);
        if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());
        Dish existing = dishMapper.selectById(id);
        if (existing == null) return Result.error(404, "菜谱不存在");
        if (ingredients != null && !ingredients.isEmpty()) {
            dish.setPrice(computeFromList(ingredients));
        }
        dishMapper.updateById(dish);
        // 删除旧关联，插入新关联
        diMapper.delete(new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, id));
        if (ingredients != null) saveIngredients(id, familyId, ingredients);
        return Result.ok();
    }

    private BigDecimal computeFromList(List<Map<String, Object>> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return BigDecimal.ZERO;
        Set<Long> ids = ingredients.stream()
                .map(m -> ((Number) m.get("ingId")).longValue())
                .collect(Collectors.toSet());
        Map<Long, Ingredient> map = ingMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, i -> i));
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> m : ingredients) {
            Long ingId = ((Number) m.get("ingId")).longValue();
            BigDecimal amount = new BigDecimal(m.get("amount").toString());
            Ingredient ing = map.get(ingId);
            if (ing != null) {
                total = total.add(ing.getPrice().multiply(amount));
            }
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void saveIngredients(Long dishId, Long familyId, List<Map<String, Object>> ingredients) {
        if (ingredients == null) return;
        for (Map<String, Object> m : ingredients) {
            DishIngredient di = new DishIngredient();
            di.setFamilyId(familyId);
            di.setDishId(dishId);
            di.setIngId(((Number) m.get("ingId")).longValue());
            di.setAmount(new BigDecimal(m.get("amount").toString()));
            di.setUnit(m.getOrDefault("unit", "").toString());
            diMapper.insert(di);
        }
    }

    // ---- 上架/下架切换（持久化）----
    public Result<Void> toggleStatus(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) return Result.error(404, "菜谱不存在");
        Result<?> check = checkFamilyChefOrOwner(dish.getFamilyId());
        if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());
        int newStatus = dish.getStatus() == 1 ? 0 : 1;
        dishMapper.update(null,
                new LambdaUpdateWrapper<Dish>()
                        .eq(Dish::getId, id)
                        .set(Dish::getStatus, newStatus));
        return Result.ok();
    }

    // ---- 删除菜谱（仅 OWNER/CHEF）----
    @Transactional
    public Result<Void> delete(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) return Result.error(404, "菜谱不存在");
        Result<?> check = checkFamilyChefOrOwner(dish.getFamilyId());
        if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());
        // 删除关联的配菜记录
        diMapper.delete(new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, id));
        // 删除菜谱本身
        dishMapper.deleteById(id);
        return Result.ok();
    }

    // ---- Jaccard 推荐算法 ----
    // threshold: 0.5~1.0，默认 0.8
    public Result<List<Dish>> recommend(List<Long> selectedIngIds, double threshold) {
        if (selectedIngIds == null || selectedIngIds.isEmpty()) return Result.ok(List.of());
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(List.of());

        List<Dish> allDishes = dishMapper.selectList(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getFamilyId, familyId)
                        .eq(Dish::getStatus, 1));

        Set<Long> selected = new HashSet<>(selectedIngIds);
        double unionSize = selected.size();

        List<Map<String, Object>> scored = new ArrayList<>();

        for (Dish dish : allDishes) {
            List<DishIngredient> dis = diMapper.selectList(
                    new LambdaQueryWrapper<DishIngredient>()
                            .eq(DishIngredient::getDishId, dish.getId()));
            if (dis.isEmpty()) continue;

            Set<Long> dishIngs = dis.stream()
                    .map(DishIngredient::getIngId).collect(Collectors.toSet());

            Set<Long> intersection = new HashSet<>(selected);
            intersection.retainAll(dishIngs);
            double intersectSize = intersection.size();

            Set<Long> union = new HashSet<>(selected);
            union.addAll(dishIngs);
            double unionRealSize = union.size();

            double jaccard = intersectSize / unionRealSize;
            if (jaccard >= threshold) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dish", dish);
                entry.put("jaccard", Math.round(jaccard * 100) / 100.0);
                entry.put("matchCount", (int) intersectSize);
                entry.put("matchIngs", dis.stream()
                        .filter(di -> selected.contains(di.getIngId()))
                        .map(di -> {
                            Ingredient ing = ingMapper.selectById(di.getIngId());
                            return ing != null ? ing.getName() : "?";
                        }).toList());
                scored.add(entry);
            }
        }

        // 按 jaccard 降序
        scored.sort((a, b) -> Double.compare(
                (Double) b.get("jaccard"), (Double) a.get("jaccard")));

        @SuppressWarnings("unchecked")
        List<Dish> result = scored.stream().map(m -> (Dish) m.get("dish")).toList();
        return Result.ok(result);
    }

    // ==================== 批量保存（前端编辑后一次性提交） ====================

    /**
     * 批量保存家庭菜单。
     * @param dishes 编辑后的完整菜品列表（id 可为空表示新增）
     * @param deletedIds 需要删除的菜品 ID 列表
     */
    @Transactional
    public Result<Map<String, Object>> batchSave(List<Map<String, Object>> dishes, List<Long> deletedIds) {
        Long familyId = UserContext.getFamily();
        Result<?> check = checkFamilyChefOrOwner(familyId);
        if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());

        int created = 0, updated = 0, deleted = 0;

        // 处理删除
        if (deletedIds != null && !deletedIds.isEmpty()) {
            for (Long id : deletedIds) {
                if (id == null) continue;
                Dish d = dishMapper.selectById(id);
                if (d != null && familyId.equals(d.getFamilyId())) {
                    // 删除关联的配菜
                    diMapper.delete(new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, id));
                    dishMapper.deleteById(id);
                    deleted++;
                }
            }
        }

        // 处理新增/更新
        if (dishes != null) {
            for (Map<String, Object> dm : dishes) {
                Long id = toLong(dm.get("id"));
                String name = (String) dm.get("name");
                if (name == null || name.isBlank()) continue;

                Dish d = new Dish();
                d.setFamilyId(familyId);
                d.setName(name.trim());
                d.setImageEmoji((String) dm.getOrDefault("imageEmoji", ""));
                d.setDescription((String) dm.getOrDefault("description", ""));
                d.setSpiceLevel(toInt(dm.get("spiceLevel"), 0));
                d.setStatus(toInt(dm.get("status"), 1));
                Long categoryId = toLong(dm.get("categoryId"));
                if (categoryId == null) categoryId = DEFAULT_CATEGORY_ID;
                d.setCategoryId(categoryId);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ingredients = (List<Map<String, Object>>) dm.get("ingredients");

                if (id == null || id <= 0) {
                    // 新增
                    if (ingredients != null && !ingredients.isEmpty()) {
                        d.setPrice(computeFromList(ingredients));
                    }
                    d.setCreatedAt(java.time.LocalDateTime.now());
                    d.setUpdatedAt(d.getCreatedAt());
                    dishMapper.insert(d);
                    saveIngredients(d.getId(), familyId, ingredients);
                    created++;
                } else {
                    // 更新
                    Dish existing = dishMapper.selectById(id);
                    if (existing == null || !familyId.equals(existing.getFamilyId())) continue;
                    d.setId(id);
                    if (ingredients != null && !ingredients.isEmpty()) {
                        d.setPrice(computeFromList(ingredients));
                    }
                    d.setUpdatedAt(java.time.LocalDateTime.now());
                    dishMapper.updateById(d);
                    // 替换配菜
                    diMapper.delete(new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, id));
                    if (ingredients != null) saveIngredients(id, familyId, ingredients);
                    updated++;
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("updated", updated);
        data.put("deleted", deleted);
        return Result.ok(data);
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) {
            log.debug("[Dish] toLong 解析失败 v={}", v, e);
            return null;
        }
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) {
            log.debug("[Dish] toInt 解析失败 v={}", v, e);
            return def;
        }
    }
}
