package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.entity.IngredientCategory;
import com.example.order.service.IngredientCategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredient-categories")
public class IngredientCategoryController {

    @Autowired private IngredientCategoryService service;

    @GetMapping
    public Result<List<IngredientCategory>> list() {
        return service.list();
    }

    @PostMapping
    public Result<IngredientCategory> create(@RequestBody @Valid IngredientCategory c) {
        return service.create(c);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IngredientCategory c) {
        c.setId(id);
        return service.update(c);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return service.delete(id);
    }
}
