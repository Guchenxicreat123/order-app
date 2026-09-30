package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.entity.Ingredient;
import com.example.order.service.IngredientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    @Autowired private IngredientService service;

    // ==================== 公开接口 ====================

    @GetMapping("/public")
    public Result<List<Ingredient>> listPublic() {
        return service.listPublic();
    }

    @GetMapping
    public Result<List<Ingredient>> listByCategory(@RequestParam(required = false) Long categoryId) {
        return service.listByCategory(categoryId);
    }

    @GetMapping("/{id}")
    public Result<Ingredient> getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // ==================== 主厨管理接口（拦截器统一鉴权）====================

    @GetMapping("/manage/all")
    public Result<List<Ingredient>> listAll() {
        return service.listAll();
    }

    @PostMapping
    public Result<Ingredient> create(@RequestBody @Valid Ingredient ing) {
        return service.create(ing);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Ingredient ing) {
        ing.setId(id);
        return service.update(ing);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    /** 批量保存配菜（编辑模式：增/删/改一次性提交） */
    @PostMapping("/batch-save")
    public Result<java.util.Map<String, Object>> batchSave(@RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> ingredients =
                (List<java.util.Map<String, Object>>) body.get("ingredients");
        @SuppressWarnings("unchecked")
        List<Object> deletedRaw = (List<Object>) body.get("deletedIds");
        List<Long> deletedIds = new ArrayList<>();
        if (deletedRaw != null) {
            for (Object o : deletedRaw) {
                if (o instanceof Number) deletedIds.add(((Number) o).longValue());
            }
        }
        return service.batchSave(ingredients, deletedIds);
    }
}
