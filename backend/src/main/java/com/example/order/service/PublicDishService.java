package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.dto.PublicDishIngredientVO;
import com.example.order.entity.*;
import com.example.order.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PublicDishService {

    @Autowired
    private PublicDishMapper publicDishMapper;

    @Autowired
    private PublicDishIngredientMapper publicDishIngredientMapper;

    @Autowired
    private PublicIngredientMapper publicIngredientMapper;

    @Autowired
    private IngredientCategoryMapper ingredientCategoryMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishIngredientMapper dishIngredientMapper;

    @Autowired
    private IngredientMapper ingredientMapper;

    /** 公开列表（无需登录，上架状态） */
    public Result<List<PublicDish>> listPublic() {
        List<PublicDish> list = publicDishMapper.selectList(
            new LambdaQueryWrapper<PublicDish>()
                .eq(PublicDish::getStatus, 1)
                .orderByAsc(PublicDish::getCategoryId)
                .orderByDesc(PublicDish::getId)
        );
        return Result.ok(list);
    }

    /**
     * 客户端聚合接口：一次拿全部上架公共菜 + 每道菜的配方
     * 适用于"小程序启动时一次性加载 20 道菜的完整数据到内存"
     * 返回结构：List<Map>，每个 Map 形如
     *   { id, name, categoryId, imageEmoji, description, spiceLevel, status, ingredients: [...] }
     */
    public Result<List<Map<String, Object>>> listPublicWithIngredients() {
        List<PublicDish> dishes = publicDishMapper.selectList(
            new LambdaQueryWrapper<PublicDish>()
                .eq(PublicDish::getStatus, 1)
                .orderByAsc(PublicDish::getCategoryId)
                .orderByAsc(PublicDish::getId)
        );
        if (dishes.isEmpty()) return Result.ok(Collections.emptyList());
        // 批量查所有配方（一次 SQL，N+1 → 1 次）
        List<Long> dishIds = dishes.stream().map(PublicDish::getId).collect(Collectors.toList());
        List<PublicDishIngredient> allRows = publicDishIngredientMapper.selectList(
            new LambdaQueryWrapper<PublicDishIngredient>()
                .in(PublicDishIngredient::getPublicDishId, dishIds)
                .orderByAsc(PublicDishIngredient::getId)
        );
        Map<Long, List<PublicDishIngredient>> rowsByDish = allRows.stream()
            .collect(Collectors.groupingBy(PublicDishIngredient::getPublicDishId));
        // 批量查 ingredient + 分类
        Set<Long> ingIds = allRows.stream().map(PublicDishIngredient::getIngId).collect(Collectors.toSet());
        Map<Long, PublicIngredient> ingMap = ingIds.isEmpty() ? Collections.emptyMap()
            : publicIngredientMapper.selectBatchIds(ingIds).stream()
                .collect(Collectors.toMap(PublicIngredient::getId, i -> i));
        Set<Long> catIds = ingMap.values().stream().map(PublicIngredient::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> catMap = catIds.isEmpty() ? Collections.emptyMap()
            : ingredientCategoryMapper.selectBatchIds(catIds).stream()
                .collect(Collectors.toMap(IngredientCategory::getId, IngredientCategory::getName));
        // 组装
        List<Map<String, Object>> result = new ArrayList<>(dishes.size());
        for (PublicDish d : dishes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("categoryId", d.getCategoryId());
            m.put("imageEmoji", d.getImageEmoji());
            m.put("description", d.getDescription());
            m.put("spiceLevel", d.getSpiceLevel());
            m.put("status", d.getStatus());
            // 组装 ingredients
            List<PublicDishIngredientVO> vos = new ArrayList<>();
            for (PublicDishIngredient r : rowsByDish.getOrDefault(d.getId(), Collections.emptyList())) {
                PublicDishIngredientVO vo = new PublicDishIngredientVO();
                vo.setId(r.getId());
                vo.setIngId(r.getIngId());
                vo.setAmount(r.getAmount());
                vo.setUnit(r.getUnit());
                PublicIngredient i = ingMap.get(r.getIngId());
                if (i != null) {
                    vo.setIngName(i.getName());
                    vo.setIngEmoji(i.getEmoji());
                    vo.setIngCategoryId(i.getCategoryId());
                    vo.setIngCategoryName(catMap.get(i.getCategoryId()));
                    vo.setUnitPrice(i.getPrice());
                }
                vos.add(vo);
            }
            m.put("ingredients", vos);
            result.add(m);
        }
        return Result.ok(result);
    }

    /** 管理员查看全部（含下架） */
    public Result<List<PublicDish>> listAll() {
        List<PublicDish> list = publicDishMapper.selectList(
            new LambdaQueryWrapper<PublicDish>()
                .orderByDesc(PublicDish::getId)
        );
        return Result.ok(list);
    }

    /** 管理员新增 */
    @Transactional
    public Result<PublicDish> create(PublicDish dish) {
        dish.setStatus(1);
        dish.setCreatedAt(LocalDateTime.now());
        dish.setUpdatedAt(LocalDateTime.now());
        publicDishMapper.insert(dish);
        return Result.ok(dish);
    }

    /** 管理员编辑 */
    @Transactional
    public Result<Void> update(Long id, PublicDish upd) {
        PublicDish existing = publicDishMapper.selectById(id);
        if (existing == null) {
            return Result.error(404, "菜品不存在");
        }
        if (upd.getName() != null) existing.setName(upd.getName());
        if (upd.getCategoryId() != null) existing.setCategoryId(upd.getCategoryId());
        if (upd.getImageEmoji() != null) existing.setImageEmoji(upd.getImageEmoji());
        if (upd.getDescription() != null) existing.setDescription(upd.getDescription());
        if (upd.getSpiceLevel() != null) existing.setSpiceLevel(upd.getSpiceLevel());
        if (upd.getStatus() != null) existing.setStatus(upd.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        publicDishMapper.updateById(existing);
        return Result.ok();
    }

    /** 管理员删除 */
    @Transactional
    public Result<Void> delete(Long id) {
        publicDishMapper.deleteById(id);
        return Result.ok();
    }

    /** 公开菜品详情（含配方） */
    public Result<Map<String, Object>> getById(Long id) {
        PublicDish dish = publicDishMapper.selectById(id);
        if (dish == null) return Result.error(404, "菜品不存在");
        return Result.ok(toDetailMap(dish));
    }

    /** 取一个菜品的所有配方（用于"详情"页和"加入菜单"时的预览） */
    public List<PublicDishIngredientVO> getIngredients(Long publicDishId) {
        List<PublicDishIngredient> rows = publicDishIngredientMapper.selectList(
            new LambdaQueryWrapper<PublicDishIngredient>()
                .eq(PublicDishIngredient::getPublicDishId, publicDishId)
                .orderByAsc(PublicDishIngredient::getId)
        );
        if (rows.isEmpty()) return Collections.emptyList();
        // 批量查 ingredient
        Set<Long> ingIds = rows.stream().map(PublicDishIngredient::getIngId).collect(Collectors.toSet());
        List<PublicIngredient> ings = publicIngredientMapper.selectBatchIds(ingIds);
        Map<Long, PublicIngredient> ingMap = ings.stream()
            .collect(Collectors.toMap(PublicIngredient::getId, i -> i));
        // 批量查分类
        Set<Long> catIds = ings.stream().map(PublicIngredient::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> catMap = new HashMap<>();
        if (!catIds.isEmpty()) {
            ingredientCategoryMapper.selectBatchIds(catIds)
                .forEach(c -> catMap.put(c.getId(), c.getName()));
        }
        // 组装 VO
        return rows.stream().map(r -> {
            PublicDishIngredientVO vo = new PublicDishIngredientVO();
            vo.setId(r.getId());
            vo.setIngId(r.getIngId());
            vo.setAmount(r.getAmount());
            vo.setUnit(r.getUnit());
            PublicIngredient i = ingMap.get(r.getIngId());
            if (i != null) {
                vo.setIngName(i.getName());
                vo.setIngEmoji(i.getEmoji());
                vo.setIngCategoryId(i.getCategoryId());
                vo.setIngCategoryName(catMap.get(i.getCategoryId()));
                vo.setUnitPrice(i.getPrice());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /** 把单个菜品 + 配方 包装成前端要的 map */
    private Map<String, Object> toDetailMap(PublicDish dish) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dish.getId());
        m.put("name", dish.getName());
        m.put("categoryId", dish.getCategoryId());
        m.put("imageEmoji", dish.getImageEmoji());
        m.put("description", dish.getDescription());
        m.put("spiceLevel", dish.getSpiceLevel());
        m.put("status", dish.getStatus());
        m.put("ingredients", getIngredients(dish.getId()));
        return m;
    }

    /** 批量查多个菜品的配方（按菜品 id 分组） */
    public Result<Map<Long, Map<String, Object>>> getManyWithIngredients(List<Long> ids) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return Result.ok(result);
        List<PublicDish> dishes = publicDishMapper.selectBatchIds(ids);
        for (PublicDish d : dishes) {
            result.put(d.getId(), toDetailMap(d));
        }
        return Result.ok(result);
    }

    // ==================== 批量加入家庭菜单 ====================

    /**
     * 把公共菜品批量加入家庭：同时复制「配方」和「配方用到的配菜」，
     * 并算出 t_dish.price，否则点菜页面会显示 ¥0。
     *
     * 三种情况：
     *  - 新菜         → 新建菜品 + 配菜 + 配方 + 价格
     *  - 已存在空壳   → 之前加入过但没配方/价格为 0（老逻辑的产物），本次补全（自愈）
     *  - 已存在完整   → skip，不动用户自己改过的数据
     */
    @Transactional
    public Result<Map<String, Object>> addToFamily(Long familyId, List<Long> publicDishIds) {
        if (familyId == null || publicDishIds == null || publicDishIds.isEmpty()) {
            return Result.error(400, "参数不完整");
        }
        List<PublicDish> publics = publicDishMapper.selectList(
            new LambdaQueryWrapper<PublicDish>()
                .in(PublicDish::getId, publicDishIds)
                .eq(PublicDish::getStatus, 1)
        );
        if (publics.isEmpty()) return Result.error(404, "未找到可加入的公共菜品");

        // ---- 预取：一次查出所有菜品配方，避免 N+1 ----
        List<Long> pdIds = publics.stream().map(PublicDish::getId).collect(Collectors.toList());
        Map<Long, List<PublicDishIngredient>> recipeByDish = publicDishIngredientMapper.selectList(
                new LambdaQueryWrapper<PublicDishIngredient>()
                    .in(PublicDishIngredient::getPublicDishId, pdIds))
            .stream().collect(Collectors.groupingBy(PublicDishIngredient::getPublicDishId));

        Set<Long> ingIds = recipeByDish.values().stream()
            .flatMap(List::stream).map(PublicDishIngredient::getIngId).collect(Collectors.toSet());

        Map<Long, PublicIngredient> publicIngMap = ingIds.isEmpty()
            ? Collections.emptyMap()
            : publicIngredientMapper.selectBatchIds(ingIds).stream()
                .collect(Collectors.toMap(PublicIngredient::getId, i -> i, (a, b) -> a));

        // 公共配菜分类：id → 名称（family_id=0）
        Map<Long, String> publicCatName = ingredientCategoryMapper.selectList(
            new LambdaQueryWrapper<IngredientCategory>().eq(IngredientCategory::getFamilyId, 0L)
        ).stream().collect(Collectors.toMap(IngredientCategory::getId, IngredientCategory::getName));

        // ---- 目标家庭现状 ----
        Map<String, Dish> familyDishByName = dishMapper.selectList(
            new LambdaQueryWrapper<Dish>().eq(Dish::getFamilyId, familyId)
        ).stream().collect(Collectors.toMap(Dish::getName, d -> d, (a, b) -> a));

        Map<String, Ingredient> familyIngByName = ingredientMapper.selectList(
            new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getFamilyId, familyId)
        ).stream().collect(Collectors.toMap(Ingredient::getName, i -> i, (a, b) -> a));

        Map<String, Long> familyCatByName = ingredientCategoryMapper.selectList(
            new LambdaQueryWrapper<IngredientCategory>().eq(IngredientCategory::getFamilyId, familyId)
        ).stream().collect(Collectors.toMap(IngredientCategory::getName, IngredientCategory::getId, (a, b) -> a));

        // 公共配菜 id → 家庭配菜 id
        Map<Long, Long> publicIngToFamilyIng = new HashMap<>();
        List<String> addedIngNames = new ArrayList<>();
        List<String> addedNames = new ArrayList<>();
        List<String> repairedNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();

        for (PublicDish pd : publics) {
            List<PublicDishIngredient> recipe =
                recipeByDish.getOrDefault(pd.getId(), Collections.emptyList());

            Dish dish = familyDishByName.get(pd.getName());
            boolean isNew = dish == null;

            if (!isNew) {
                boolean hasRecipe = dishIngredientMapper.selectCount(
                    new LambdaQueryWrapper<DishIngredient>()
                        .eq(DishIngredient::getDishId, dish.getId())) > 0;
                boolean hasPrice = dish.getPrice() != null
                    && dish.getPrice().compareTo(BigDecimal.ZERO) > 0;
                // 完整的老菜：不动用户的修改
                if (hasRecipe && hasPrice) {
                    skippedNames.add(pd.getName());
                    continue;
                }
                // 没有配方可用（如凉拌木耳）：补不了，只当 skip
                if (recipe.isEmpty()) {
                    skippedNames.add(pd.getName());
                    continue;
                }
            }

            // 1) 确保该菜品用到的配菜都已进入家庭配菜库
            for (PublicDishIngredient r : recipe) {
                if (publicIngToFamilyIng.containsKey(r.getIngId())) continue;
                PublicIngredient pi = publicIngMap.get(r.getIngId());
                if (pi == null) continue;

                Ingredient fi = familyIngByName.get(pi.getName());
                if (fi == null) {
                    Long targetCatId = resolveFamilyCategoryId(
                        familyId, pi.getCategoryId(), publicCatName, familyCatByName);
                    fi = new Ingredient();
                    fi.setFamilyId(familyId);
                    fi.setName(pi.getName());
                    fi.setCategoryId(targetCatId != null ? targetCatId : pi.getCategoryId());
                    fi.setUnit(pi.getUnit() != null ? pi.getUnit() : "克");
                    fi.setPrice(pi.getPrice() != null ? pi.getPrice() : BigDecimal.ZERO);
                    fi.setEmoji(pi.getEmoji() != null ? pi.getEmoji() : "");
                    fi.setStatus(1);
                    fi.setCreatedAt(LocalDateTime.now());
                    fi.setUpdatedAt(LocalDateTime.now());
                    ingredientMapper.insert(fi);
                    familyIngByName.put(fi.getName(), fi);
                    addedIngNames.add(fi.getName());
                }
                publicIngToFamilyIng.put(r.getIngId(), fi.getId());
            }

            // 2) 先算价（点菜页面直接读 t_dish.price，算不出来就是 ¥0）
            BigDecimal total = BigDecimal.ZERO;
            for (PublicDishIngredient r : recipe) {
                PublicIngredient pi = publicIngMap.get(r.getIngId());
                if (pi != null && pi.getPrice() != null && r.getAmount() != null) {
                    total = total.add(pi.getPrice().multiply(r.getAmount()));
                }
            }
            total = total.setScale(2, java.math.RoundingMode.HALF_UP);

            // 3) 建/更新家庭菜品
            if (isNew) {
                dish = new Dish();
                dish.setName(pd.getName());
                dish.setCategoryId(pd.getCategoryId());
                dish.setImageEmoji(pd.getImageEmoji() != null ? pd.getImageEmoji() : "");
                dish.setDescription(pd.getDescription() != null ? pd.getDescription() : "");
                dish.setSpiceLevel(pd.getSpiceLevel() != null ? pd.getSpiceLevel() : 0);
                dish.setStatus(1);
                dish.setFamilyId(familyId);
                dish.setCreatedAt(LocalDateTime.now());
            }
            dish.setPrice(total);
            dish.setUpdatedAt(LocalDateTime.now());
            if (isNew) {
                dishMapper.insert(dish);
                familyDishByName.put(dish.getName(), dish);
            } else {
                dishMapper.updateById(dish);
                // 补全场景：清掉旧的空配方，避免重复
                dishIngredientMapper.delete(new LambdaQueryWrapper<DishIngredient>()
                    .eq(DishIngredient::getDishId, dish.getId()));
            }

            // 4) 复制配方 t_public_dish_ingredient → t_dish_ingredient
            for (PublicDishIngredient r : recipe) {
                Long familyIngId = publicIngToFamilyIng.get(r.getIngId());
                if (familyIngId == null) continue;
                DishIngredient di = new DishIngredient();
                di.setFamilyId(familyId);
                di.setDishId(dish.getId());
                di.setIngId(familyIngId);
                di.setAmount(r.getAmount());
                di.setUnit(r.getUnit() != null ? r.getUnit() : "");
                dishIngredientMapper.insert(di);
            }

            if (isNew) addedNames.add(dish.getName());
            else repairedNames.add(dish.getName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addedCount", addedNames.size());
        result.put("repairedCount", repairedNames.size());
        result.put("skippedCount", skippedNames.size());
        result.put("addedNames", addedNames);
        result.put("repairedNames", repairedNames);
        result.put("skippedNames", skippedNames);
        result.put("addedIngredientCount", addedIngNames.size());
        result.put("addedIngredientNames", addedIngNames);
        return Result.ok(result);
    }

    /**
     * 把公共配菜的分类映射到目标家庭的分类（按名称匹配），
     * 家庭里没有同名分类则新建一个。返回 null 表示无法确定。
     */
    private Long resolveFamilyCategoryId(Long familyId, Long publicCatId,
                                         Map<Long, String> publicCatName,
                                         Map<String, Long> familyCatByName) {
        String catName = publicCatName.get(publicCatId);
        if (catName == null) return null;
        Long existing = familyCatByName.get(catName);
        if (existing != null) return existing;

        IngredientCategory newCat = new IngredientCategory();
        newCat.setFamilyId(familyId);
        newCat.setName(catName);
        newCat.setEmoji("");
        newCat.setSort(0);
        newCat.setCreatedAt(LocalDateTime.now());
        ingredientCategoryMapper.insert(newCat);
        familyCatByName.put(catName, newCat.getId());
        return newCat.getId();
    }
}
