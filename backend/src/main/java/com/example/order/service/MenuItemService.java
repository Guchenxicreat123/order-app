package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.*;
import com.example.order.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class MenuItemService {

    @Autowired private MenuItemMapper menuItemMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private DishIngredientMapper diMapper;
    @Autowired private IngredientMapper ingMapper;
    @Autowired private DishService dishService;
    @Autowired private ChefService chefService;
    @Autowired private UserMapper userMapper;

    // ==================== 下单 ====================

    @Transactional
    public Result<Map<String, Object>> addItem(
            String userId, String nickname,
            Long dishId, String dishName, String dishEmoji,
            String customIngs, // JSON
            Integer spiceLevel, String remark) {

        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.error(400, "请先选择家庭");

        // 校验：家庭必须有主厨才能下单
        if (!chefService.hasChef(familyId)) {
            return Result.error(400, "当前家庭还没有主厨，请先让家庭成员认领主厨再下单");
        }

        if (dishId != null) {
            // 固定菜：价格从菜谱读
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null) return Result.error(404, "菜不存在");
            dishName = dish.getName();
            dishEmoji = dish.getImageEmoji() != null ? dish.getImageEmoji() : "";
            if (spiceLevel == null) spiceLevel = dish.getSpiceLevel() != null ? dish.getSpiceLevel() : 0;
        } else if (dishName == null || dishName.isBlank()) {
            // 自定义菜未指定名称时自动生成
            dishName = "自定义菜";
        }

        // 计算价格
        BigDecimal price = computePrice(dishId, customIngs);

        MenuItem item = new MenuItem();
        item.setUserId(userId);
        item.setFamilyId(familyId);
        item.setUserNickname(nickname != null ? nickname : "");
        item.setDishId(dishId);
        item.setDishName(dishName);
        item.setDishEmoji(dishEmoji != null ? dishEmoji : "");
        item.setCustomIngs(customIngs);
        item.setSpiceLevel(spiceLevel != null ? spiceLevel : 0);
        item.setPrice(price);
        item.setRemark(remark);
        item.setStatus(0); // 已下单
        item.setVersion(0);
        item.setCreatedAt(LocalDateTime.now());
        menuItemMapper.insert(item);

        return Result.ok(Map.of("id", item.getId(), "price", price));
    }

    private BigDecimal computePrice(Long dishId, String customIngs) {
        if (dishId != null) {
            return dishService.computeDishPrice(dishId);
        }
        // 自定义菜：从 JSON 算
        return computeCustomPrice(customIngs);
    }

    private BigDecimal computeCustomPrice(String customIngs) {
        if (customIngs == null || customIngs.isBlank()) return BigDecimal.ZERO;
        try {
            List<Map> list = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(customIngs, List.class);
            if (list.isEmpty()) return BigDecimal.ZERO;
            Set<Long> ids = new HashSet<>();
            for (Map m : list) {
                ids.add(((Number) m.get("ingId")).longValue());
            }
            Map<Long, Ingredient> map = ingMapper.selectBatchIds(ids).stream()
                    .collect(java.util.stream.Collectors.toMap(Ingredient::getId, i -> i));
            BigDecimal total = BigDecimal.ZERO;
            for (Map m : list) {
                Long ingId = ((Number) m.get("ingId")).longValue();
                BigDecimal amount = new BigDecimal(m.get("amount").toString());
                Ingredient ing = map.get(ingId);
                if (ing != null) {
                    total = total.add(ing.getPrice().multiply(amount));
                }
            }
            return total.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[Menu] 自定义菜价格计算失败，视为 0", e);
            return BigDecimal.ZERO;
        }
    }

    // ==================== 今日菜单 ====================

    public Result<List<Map<String, Object>>> todayMenu() {
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        List<MenuItem> items = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>()
                        .eq(MenuItem::getFamilyId, familyId)
                        .apply("DATE(created_at) = CURDATE()")
                        .orderByDesc(MenuItem::getCreatedAt));
        return Result.ok(enrich(items));
    }

    /** 按用户聚合的菜单（默认今天，传入 date 参数则按指定日期） */
    public Result<List<Map<String, Object>>> todayMenuByUser(LocalDate date) {
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        List<MenuItem> items;
        if (date == null) {
            items = menuItemMapper.selectList(
                    new LambdaQueryWrapper<MenuItem>()
                            .eq(MenuItem::getFamilyId, familyId)
                            .apply("DATE(created_at) = CURDATE()")
                            .orderByDesc(MenuItem::getCreatedAt));
        } else {
            items = menuItemMapper.selectList(
                    new LambdaQueryWrapper<MenuItem>()
                            .eq(MenuItem::getFamilyId, familyId)
                            .apply("DATE(created_at) = {0}", date)
                            .orderByDesc(MenuItem::getCreatedAt));
        }
        Map<String, List<MenuItem>> byUser = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(MenuItem::getUserId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<MenuItem>> e : byUser.entrySet()) {
            Map<String, Object> userEntry = new LinkedHashMap<>();
            userEntry.put("userId", e.getKey());
            userEntry.put("nickname", e.getValue().get(0).getUserNickname());
            userEntry.put("items", enrich(e.getValue()));
            result.add(userEntry);
        }
        return Result.ok(result);
    }

    /** 按用户聚合的菜单（兼容旧调用：默认今天） */
    public Result<List<Map<String, Object>>> todayMenuByUser() {
        return todayMenuByUser(null);
    }

    private List<Map<String, Object>> enrich(List<MenuItem> items) {
        return items.stream().map(this::toVO).toList();
    }

    private Map<String, Object> toVO(MenuItem item) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", item.getId());
        vo.put("userId", item.getUserId());
        vo.put("userNickname", item.getUserNickname());
        vo.put("dishId", item.getDishId());
        vo.put("dishName", item.getDishName());
        vo.put("dishEmoji", item.getDishEmoji());
        vo.put("spiceLevel", item.getSpiceLevel());
        vo.put("price", item.getPrice());
        vo.put("remark", item.getRemark());
        vo.put("status", item.getStatus());
        vo.put("statusText", statusText(item.getStatus()));
        vo.put("confirmedBy", item.getConfirmedBy());
        vo.put("confirmedAt", item.getConfirmedAt());
        vo.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
        return vo;
    }

    private String statusText(Integer s) {
        if (s == null) return "-";
        return switch (s) {
            case -1 -> "已撤销";
            case 0 -> "已下单";
            case 1 -> "已确认";
            default -> "未知";
        };
    }

    // ==================== 确认（主厨权限，乐观锁）====================

    @Transactional
    public Result<Void> confirm(Long itemId) {
        String chefId = UserContext.get();
        if (chefId == null || !chefService.isChef(chefId)) {
            return Result.error(403, "需要主厨权限");
        }

        MenuItem item = menuItemMapper.selectById(itemId);
        if (item == null) return Result.error(404, "菜单项不存在");
        if (item.getStatus() != 0) {
            return Result.error(400, "该菜已确认或已撤销，不能重复确认");
        }

        int updated = menuItemMapper.confirmWithLock(itemId, 1, chefId, item.getVersion());
        if (updated == 0) {
            return Result.error(409, "并发冲突，请重试");
        }
        return Result.ok();
    }

    // ==================== 撤销（下单人可撤销自己，主厨可撤销任何人）====================

    @Transactional
    public Result<Void> cancel(Long itemId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");

        MenuItem item = menuItemMapper.selectById(itemId);
        if (item == null) return Result.error(404, "菜单项不存在");

        boolean isChef = chefService.isChef(userId);
        boolean isOwner = item.getUserId().equals(userId);

        if (!isOwner && !isChef) {
            return Result.error(403, "只能撤销自己的菜");
        }
        if (item.getStatus() == 1 && !isChef) {
            return Result.error(400, "已确认的菜需主厨撤销");
        }
        if (item.getStatus() != 0 && item.getStatus() != 1) {
            return Result.error(400, "该菜已撤销");
        }

        int updated = menuItemMapper.cancelWithLock(itemId, item.getVersion());
        if (updated == 0) {
            return Result.error(409, "并发冲突，请重试");
        }
        return Result.ok();
    }

    // ==================== 今日总金额 ====================
    public Result<BigDecimal> todayTotal() {
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.ok(BigDecimal.ZERO);
        List<MenuItem> items = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>()
                        .eq(MenuItem::getFamilyId, familyId)
                        .apply("DATE(created_at) = CURDATE()")
                        .in(MenuItem::getStatus, 0, 1));
        BigDecimal total = items.stream()
                .map(MenuItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Result.ok(total);
    }
}
