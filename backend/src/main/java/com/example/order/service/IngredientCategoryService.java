package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.entity.IngredientCategory;
import com.example.order.mapper.IngredientCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IngredientCategoryService {

    @Autowired private IngredientCategoryMapper mapper;

    public Result<List<IngredientCategory>> list() {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(java.util.Collections.emptyList());
        return Result.ok(mapper.selectList(
                new LambdaQueryWrapper<IngredientCategory>()
                        .eq(IngredientCategory::getFamilyId, familyId)
                        .orderByAsc(IngredientCategory::getSort)
                        .orderByAsc(IngredientCategory::getId)));
    }

    public Result<IngredientCategory> create(IngredientCategory c) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.error(400, "请先选择家庭");
        c.setFamilyId(familyId);
        mapper.insert(c);
        return Result.ok(c);
    }

    public Result<Void> update(IngredientCategory c) {
        mapper.updateById(c);
        return Result.ok();
    }

    public Result<Void> delete(Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }
}
