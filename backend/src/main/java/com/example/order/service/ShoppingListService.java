package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.entity.DishIngredient;
import com.example.order.entity.Ingredient;
import com.example.order.entity.MenuItem;
import com.example.order.mapper.DishIngredientMapper;
import com.example.order.mapper.IngredientMapper;
import com.example.order.mapper.MenuItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 买菜清单聚合服务 */
@Service
@Slf4j
public class ShoppingListService {

    @Autowired private MenuItemMapper menuItemMapper;
    @Autowired private DishIngredientMapper diMapper;
    @Autowired private IngredientMapper ingMapper;

    /**
     * 今日买菜清单（按配菜聚合克数/单位）
     * key = 配菜ID，value = {name, unit, totalAmount, price, emoji}
     */
    public Result<Map<Long, Map<String, Object>>> todayList() {
        return buildList(LocalDate.now());
    }

    /** 指定日期的买菜清单 */
    public Result<Map<Long, Map<String, Object>>> listByDate(LocalDate date) {
        return buildList(date);
    }

    private Result<Map<Long, Map<String, Object>>> buildList(LocalDate date) {
        Long familyId = com.example.order.common.UserContext.getFamily();
        if (familyId == null) return Result.ok(new LinkedHashMap<>());
        // 查指定家庭当天的菜
        List<MenuItem> items = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>()
                        .eq(MenuItem::getFamilyId, familyId)
                        .apply(date != null ? "DATE(created_at) = {0}" : null,
                                date != null ? date.toString() : LocalDate.now().toString())
                        .in(MenuItem::getStatus, 0, 1));

        // 聚合
        Map<Long, Map<String, Object>> aggregated = new LinkedHashMap<>();

        for (MenuItem item : items) {
            if (item.getDishId() != null) {
                // 固定菜：从 t_dish_ingredient 查
                List<DishIngredient> dis = diMapper.selectList(
                        new LambdaQueryWrapper<DishIngredient>()
                                .eq(DishIngredient::getDishId, item.getDishId()));
                for (DishIngredient di : dis) {
                    addToAgg(aggregated, di.getIngId(), di.getAmount(), di.getUnit(), item.getDishName());
                }
            } else if (item.getCustomIngs() != null && !item.getCustomIngs().isBlank()) {
                // 自定义菜：从 JSON 解析
                try {
                    List<Map> list = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(item.getCustomIngs(), List.class);
                    for (Map m : list) {
                        Long ingId = ((Number) m.get("ingId")).longValue();
                        BigDecimal amount = new BigDecimal(m.get("amount").toString());
                        String unit = m.getOrDefault("unit", "").toString();
                        addToAgg(aggregated, ingId, amount, unit, item.getDishName());
                    }
                } catch (Exception e) {
                    log.error("[ShoppingList] JSON 解析失败 menuItemId={}", item.getId(), e);
                }
            }
        }

        return Result.ok(aggregated);
    }

    private void addToAgg(Map<Long, Map<String, Object>> agg, Long ingId,
                           BigDecimal amount, String unit, String fromDish) {
        Map<String, Object> entry = agg.computeIfAbsent(ingId, k -> {
            Ingredient ing = ingMapper.selectById(ingId);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("ingId", ingId);
            e.put("name", ing != null ? ing.getName() : "?");
            e.put("unit", unit);
            e.put("price", ing != null ? ing.getPrice() : BigDecimal.ZERO);
            e.put("emoji", ing != null ? (ing.getEmoji() != null ? ing.getEmoji() : "") : "");
            e.put("totalAmount", BigDecimal.ZERO);
            e.put("totalCost", BigDecimal.ZERO);
            e.put("fromDishes", new ArrayList<String>());
            return e;
        });

        entry.put("totalAmount",
                ((BigDecimal) entry.get("totalAmount")).add(amount));
        entry.put("totalCost",
                ((BigDecimal) entry.get("totalCost"))
                        .add(((BigDecimal) entry.get("price")).multiply(amount)));

        ((List<String>) entry.get("fromDishes")).add(fromDish);
    }
}
