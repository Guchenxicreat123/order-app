package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.entity.Ingredient;
import com.example.order.mapper.DishIngredientMapper;
import com.example.order.mapper.IngredientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IngredientService {

    @Autowired private IngredientMapper mapper;
    @Autowired private DishIngredientMapper diMapper;

    /** 公开列表（仅显示当前家庭启用的配菜） */
    public Result<List<Ingredient>> listPublic() {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(java.util.Collections.emptyList());
        return Result.ok(mapper.selectList(
                new LambdaQueryWrapper<Ingredient>()
                        .eq(Ingredient::getFamilyId, familyId)
                        .eq(Ingredient::getStatus, 1)
                        .orderByAsc(Ingredient::getCategoryId)
                        .orderByAsc(Ingredient::getId)));
    }

    /** 管理端列表（主厨用，含全部状态） */
    public Result<List<Ingredient>> listAll() {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(java.util.Collections.emptyList());
        return Result.ok(mapper.selectList(
                new LambdaQueryWrapper<Ingredient>()
                        .eq(Ingredient::getFamilyId, familyId)
                        .orderByAsc(Ingredient::getCategoryId)
                        .orderByAsc(Ingredient::getId)));
    }

    public Result<List<Ingredient>> listByCategory(Long categoryId) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        return Result.ok(mapper.selectList(
                new LambdaQueryWrapper<Ingredient>()
                        .eq(familyId != null, Ingredient::getFamilyId, familyId)
                        .eq(categoryId != null, Ingredient::getCategoryId, categoryId)
                        .eq(Ingredient::getStatus, 1)
                        .orderByAsc(Ingredient::getId)));
    }

    public Result<Ingredient> getById(Long id) {
        Ingredient ing = mapper.selectById(id);
        if (ing == null) return Result.error(404, "配菜不存在");
        return Result.ok(ing);
    }

    public Result<Ingredient> create(Ingredient ing) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.error(400, "请先选择家庭");
        ing.setFamilyId(familyId);
        mapper.insert(ing);
        return Result.ok(ing);
    }

    public Result<Void> update(Ingredient ing) {
        mapper.updateById(ing);
        return Result.ok();
    }

    /** 删除前检查是否被菜谱引用 */
    public Result<Void> delete(Long id) {
        long refCount = diMapper.selectCount(
                new LambdaQueryWrapper<com.example.order.entity.DishIngredient>()
                        .eq(com.example.order.entity.DishIngredient::getIngId, id));
        if (refCount > 0) {
            return Result.error(400, "该配菜已被 " + refCount + " 个菜谱引用，无法删除");
        }
        mapper.deleteById(id);
        return Result.ok();
    }

    /** 批量保存配菜（编辑模式） */
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> batchSave(List<Map<String, Object>> ingredients, List<Long> deletedIds) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.error(400, "请先选择家庭");

        int created = 0, updated = 0, deleted = 0;

        if (deletedIds != null && !deletedIds.isEmpty()) {
            for (Long id : deletedIds) {
                if (id == null) continue;
                Ingredient ing = mapper.selectById(id);
                if (ing != null && familyId.equals(ing.getFamilyId())) {
                    long refCount = diMapper.selectCount(
                            new LambdaQueryWrapper<com.example.order.entity.DishIngredient>()
                                    .eq(com.example.order.entity.DishIngredient::getIngId, id));
                    if (refCount > 0) continue; // 被引用则跳过
                    mapper.deleteById(id);
                    deleted++;
                }
            }
        }

        if (ingredients != null) {
            for (Map<String, Object> im : ingredients) {
                Long id = toLong(im.get("id"));
                String name = (String) im.get("name");
                if (name == null || name.isBlank()) continue;

                Ingredient ing = new Ingredient();
                ing.setFamilyId(familyId);
                ing.setName(name.trim());
                ing.setEmoji((String) im.getOrDefault("emoji", ""));
                ing.setUnit((String) im.getOrDefault("unit", ""));
                ing.setPrice(toBigDecimal(im.get("price")));
                ing.setStatus(toInt(im.get("status"), 1));
                Long categoryId = toLong(im.get("categoryId"));
                if (categoryId != null) ing.setCategoryId(categoryId);

                if (id == null || id <= 0) {
                    ing.setCreatedAt(java.time.LocalDateTime.now());
                    ing.setUpdatedAt(ing.getCreatedAt());
                    mapper.insert(ing);
                    created++;
                } else {
                    Ingredient existing = mapper.selectById(id);
                    if (existing == null || !familyId.equals(existing.getFamilyId())) continue;
                    ing.setId(id);
                    ing.setUpdatedAt(java.time.LocalDateTime.now());
                    mapper.updateById(ing);
                    updated++;
                }
            }
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("created", created);
        data.put("updated", updated);
        data.put("deleted", deleted);
        return Result.ok(data);
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) {
            log.debug("[Ingredient] toLong 解析失败 v={}", v, e);
            return null;
        }
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) {
            log.debug("[Ingredient] toInt 解析失败 v={}", v, e);
            return def;
        }
    }

    private static java.math.BigDecimal toBigDecimal(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(v.toString()); } catch (Exception e) {
            log.debug("[Ingredient] toBigDecimal 解析失败 v={}", v, e);
            return java.math.BigDecimal.ZERO;
        }
    }

    public Result<Void> toggleStatus(Long id) {
        Ingredient ing = mapper.selectById(id);
        if (ing == null) return Result.error(404, "配菜不存在");
        ing.setStatus(ing.getStatus() == 1 ? 0 : 1);
        mapper.updateById(ing);
        return Result.ok();
    }
}
