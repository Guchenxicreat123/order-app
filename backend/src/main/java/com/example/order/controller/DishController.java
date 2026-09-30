package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.dto.DishReq;
import com.example.order.entity.Dish;
import com.example.order.service.DishService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dishes")
public class DishController {

    @Autowired private DishService dishService;

    // ==================== 公开接口 ====================

    @GetMapping
    public Result<List<Dish>> list() {
        return dishService.list();
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return dishService.getDetail(id);
    }

    // ==================== Jaccard 推荐 ====================

    @GetMapping("/recommend")
    public Result<List<Dish>> recommend(
            @RequestParam List<Long> ingIds,
            @RequestParam(defaultValue = "0.8") double threshold) {
        if (threshold < 0.5 || threshold > 1.0) threshold = 0.8;
        return dishService.recommend(ingIds, threshold);
    }

    // ==================== 主厨管理接口（拦截器统一鉴权）====================

    @GetMapping("/manage/all")
    public Result<List<Dish>> listAll() {
        return dishService.listAll();
    }

    /** 小程序端：当前家庭完整菜品列表（含已下架，每菜附配菜配方），要求 OWNER/CHEF */
    @GetMapping("/all-family")
    public Result<List<Map<String, Object>>> listAllFamily() {
        return dishService.listAllForCurrentFamilyWithIngredients();
    }

    @PostMapping
    public Result<Dish> create(@RequestBody @Valid DishReq req) {
        Dish dish = new Dish();
        dish.setName(req.getName());
        dish.setCategoryId(req.getCategoryId());
        dish.setImageEmoji(req.getImageEmoji());
        dish.setDescription(req.getDescription());
        dish.setSpiceLevel(req.getSpiceLevel());
        dish.setStatus(1);
        List<Map<String, Object>> ingredients = req.getIngredients() != null
                ? req.getIngredients().stream().map(r -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("ingId", r.getIngId());
                    m.put("amount", r.getAmount());
                    m.put("unit", r.getUnit() != null ? r.getUnit() : "");
                    return m;
                }).toList()
                : List.of();
        return dishService.create(dish, ingredients);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid DishReq req) {
        Dish dish = dishService.listAll().getData().stream()
                .filter(d -> d.getId().equals(id))
                .findFirst().orElse(null);
        if (dish == null) return Result.error(404, "菜谱不存在");
        dish.setName(req.getName());
        dish.setCategoryId(req.getCategoryId());
        dish.setImageEmoji(req.getImageEmoji());
        dish.setDescription(req.getDescription());
        dish.setSpiceLevel(req.getSpiceLevel());
        List<Map<String, Object>> ingredients = req.getIngredients() != null
                ? req.getIngredients().stream().map(r -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("ingId", r.getIngId());
                    m.put("amount", r.getAmount());
                    m.put("unit", r.getUnit() != null ? r.getUnit() : "");
                    return m;
                }).toList()
                : List.of();
        return dishService.update(id, dish, ingredients);
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        return dishService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return dishService.delete(id);
    }

    /**
     * 批量保存菜单（编辑模式：一次性提交增/删/改）
     * body: { dishes: [{id?, name, imageEmoji, categoryId, description, spiceLevel, status, ingredients?}, ...], deletedIds: [...] }
     */
    @PostMapping("/batch-save")
    public Result<java.util.Map<String, Object>> batchSave(@RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> dishes =
                (List<java.util.Map<String, Object>>) body.get("dishes");
        @SuppressWarnings("unchecked")
        List<Object> deletedRaw = (List<Object>) body.get("deletedIds");
        List<Long> deletedIds = new ArrayList<>();
        if (deletedRaw != null) {
            for (Object o : deletedRaw) {
                if (o instanceof Number) deletedIds.add(((Number) o).longValue());
            }
        }
        return dishService.batchSave(dishes, deletedIds);
    }
}
