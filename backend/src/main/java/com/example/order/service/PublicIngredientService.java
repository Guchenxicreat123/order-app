package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.entity.Ingredient;
import com.example.order.entity.IngredientCategory;
import com.example.order.entity.PublicIngredient;
import com.example.order.mapper.IngredientCategoryMapper;
import com.example.order.mapper.IngredientMapper;
import com.example.order.mapper.PublicIngredientMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PublicIngredientService {

    @Autowired
    private PublicIngredientMapper publicIngredientMapper;

    @Autowired
    private IngredientMapper ingredientMapper;

    @Autowired
    private IngredientCategoryMapper ingredientCategoryMapper;

    /** 公共配菜分类（family_id=0 的全局参考分类） */
    public Result<List<IngredientCategory>> listCategories() {
        List<IngredientCategory> list = ingredientCategoryMapper.selectList(
            new LambdaQueryWrapper<IngredientCategory>()
                .eq(IngredientCategory::getFamilyId, 0L)
                .orderByAsc(IngredientCategory::getSort)
                .orderByAsc(IngredientCategory::getId)
        );
        return Result.ok(list);
    }

    /** 公开列表（无需登录，上架状态） */
    public Result<List<PublicIngredient>> listPublic() {
        List<PublicIngredient> list = publicIngredientMapper.selectList(
            new LambdaQueryWrapper<PublicIngredient>()
                .eq(PublicIngredient::getStatus, 1)
                .orderByAsc(PublicIngredient::getCategoryId)
                .orderByAsc(PublicIngredient::getId)
        );
        return Result.ok(list);
    }

    /** 管理员查看全部（含下架） */
    public Result<List<PublicIngredient>> listAll() {
        List<PublicIngredient> list = publicIngredientMapper.selectList(
            new LambdaQueryWrapper<PublicIngredient>()
                .orderByAsc(PublicIngredient::getCategoryId)
                .orderByAsc(PublicIngredient::getId)
        );
        return Result.ok(list);
    }

    /** 管理员新增 */
    @Transactional
    public Result<PublicIngredient> create(PublicIngredient ing) {
        if (ing.getStatus() == null) ing.setStatus(1);
        ing.setCreatedAt(LocalDateTime.now());
        ing.setUpdatedAt(LocalDateTime.now());
        publicIngredientMapper.insert(ing);
        return Result.ok(ing);
    }

    /** 管理员编辑 */
    @Transactional
    public Result<Void> update(Long id, PublicIngredient upd) {
        PublicIngredient existing = publicIngredientMapper.selectById(id);
        if (existing == null) {
            return Result.error(404, "配菜不存在");
        }
        if (upd.getName() != null) existing.setName(upd.getName());
        if (upd.getCategoryId() != null) existing.setCategoryId(upd.getCategoryId());
        if (upd.getUnit() != null) existing.setUnit(upd.getUnit());
        if (upd.getPrice() != null) existing.setPrice(upd.getPrice());
        if (upd.getEmoji() != null) existing.setEmoji(upd.getEmoji());
        if (upd.getStatus() != null) existing.setStatus(upd.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        publicIngredientMapper.updateById(existing);
        return Result.ok();
    }

    /** 管理员删除 */
    @Transactional
    public Result<Void> delete(Long id) {
        publicIngredientMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 批量加入家庭配菜 ====================

    @Transactional
    public Result<Map<String, Object>> addToFamily(Long familyId, List<Long> publicIngredientIds) {
        if (familyId == null || publicIngredientIds == null || publicIngredientIds.isEmpty()) {
            return Result.error(400, "参数不完整");
        }
        List<PublicIngredient> publics = publicIngredientMapper.selectList(
            new LambdaQueryWrapper<PublicIngredient>()
                .in(PublicIngredient::getId, publicIngredientIds)
                .eq(PublicIngredient::getStatus, 1)
        );
        if (publics.isEmpty()) return Result.error(404, "未找到可加入的公共配菜");

        Set<String> existingNames = ingredientMapper.selectList(
            new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getFamilyId, familyId)
        ).stream().map(Ingredient::getName).collect(Collectors.toSet());

        // 目标家庭已有的分类（按名称映射公共分类 → 家庭分类）
        Map<String, Long> familyCatByName = ingredientCategoryMapper.selectList(
            new LambdaQueryWrapper<IngredientCategory>().eq(IngredientCategory::getFamilyId, familyId)
        ).stream().collect(Collectors.toMap(IngredientCategory::getName, IngredientCategory::getId, (a, b) -> a));

        // 公共分类名（family_id=0）→ id，用于把公共配菜分类映射成目标家庭的分类
        Map<Long, String> publicCatName = ingredientCategoryMapper.selectList(
            new LambdaQueryWrapper<IngredientCategory>().eq(IngredientCategory::getFamilyId, 0L)
        ).stream().collect(Collectors.toMap(IngredientCategory::getId, IngredientCategory::getName));

        List<String> addedNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();

        for (PublicIngredient pi : publics) {
            if (existingNames.contains(pi.getName())) {
                skippedNames.add(pi.getName());
                continue;
            }
            Long targetCatId = null;
            String catName = publicCatName.get(pi.getCategoryId());
            if (catName != null) targetCatId = familyCatByName.get(catName);
            if (targetCatId == null) targetCatId = pi.getCategoryId(); // 找不到映射则保留原ID

            Ingredient ing = new Ingredient();
            ing.setName(pi.getName());
            ing.setCategoryId(targetCatId);
            ing.setUnit(pi.getUnit() != null ? pi.getUnit() : "克");
            ing.setPrice(pi.getPrice() != null ? pi.getPrice() : BigDecimal.ZERO);
            ing.setEmoji(pi.getEmoji() != null ? pi.getEmoji() : "");
            ing.setStatus(1);
            ing.setFamilyId(familyId);
            ing.setCreatedAt(LocalDateTime.now());
            ing.setUpdatedAt(LocalDateTime.now());
            ingredientMapper.insert(ing);
            addedNames.add(pi.getName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addedCount", addedNames.size());
        result.put("skippedCount", skippedNames.size());
        result.put("addedNames", addedNames);
        result.put("skippedNames", skippedNames);
        return Result.ok(result);
    }
}
