package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.dto.CreateMenuItemReq;
import com.example.order.entity.MenuItem;
import com.example.order.mapper.MenuItemMapper;
import com.example.order.service.MenuItemService;
import com.example.order.service.PushService;
import com.example.order.service.ShoppingListService;
import com.example.order.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/menu")
public class MenuItemController {

    @Autowired private MenuItemService menuItemService;
    @Autowired private PushService pushService;
    @Autowired private ShoppingListService shoppingListService;
    @Autowired private UserMapper userMapper;

    // ==================== 今日菜单（公开，登录用户）====================

    @GetMapping("/today")
    public Result<List<Map<String, Object>>> todayMenu() {
        return menuItemService.todayMenu();
    }

    @GetMapping("/today/by-user")
    public Result<List<Map<String, Object>>> todayMenuByUser(
            @RequestParam(required = false) String date) {
        LocalDate target = null;
        if (date != null && !date.isBlank()) {
            try { target = LocalDate.parse(date); } catch (Exception e) {
                log.warn("[Menu] 日期格式错误 date={}, 忽略并返回今日", date);
            }
        }
        return menuItemService.todayMenuByUser(target);
    }

    @GetMapping("/today/total")
    public Result<BigDecimal> todayTotal() {
        return menuItemService.todayTotal();
    }

    // ==================== 下单 ====================

    @PostMapping("/items")
    public Result<Map<String, Object>> addItem(@RequestBody @Valid CreateMenuItemReq req) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");

        com.example.order.entity.User u = userMapper.selectById(userId);
        String nickname = u != null && u.getNickname() != null ? u.getNickname() : "";

        Result<Map<String, Object>> r = menuItemService.addItem(
                userId, nickname,
                req.getDishId(), req.getDishName(), null,
                req.getCustomIngs(),
                req.getSpiceLevel(),
                req.getRemark());

        if (r.getCode() == 0) {
            // 通知主厨（异步）。这里拿到的是**菜品 id** 不是订单 id：
            // t_menu_item.id 与 t_order.id 是两条独立自增序列，传错会把 id 当成订单号查，
            // 必须走 pushNewOrderForItem（挂在订单下的会自动转成订单级推送）。
            Long itemId = (Long) r.getData().get("id");
            pushService.pushAfterCommit(() -> pushService.pushNewOrderForItem(itemId));
        }
        return r;
    }

    // ==================== 确认（主厨权限）====================

    @PutMapping("/items/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        Result<Void> r = menuItemService.confirm(id);
        if (r.getCode() == 0) {
            // 通知下单人
            pushService.pushAfterCommit(() -> pushService.pushStatusChanged(id));
        }
        return r;
    }

    // ==================== 撤销 ====================

    @DeleteMapping("/items/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        Result<Void> r = menuItemService.cancel(id);
        if (r.getCode() == 0) {
            pushService.pushAfterCommit(() -> pushService.pushStatusChanged(id));
        }
        return r;
    }

    // ==================== 买菜清单 ====================

    @GetMapping("/shopping-list")
    public Result<Map<Long, Map<String, Object>>> shoppingList(
            @RequestParam(required = false) String date) {
        if (date != null && !date.isBlank()) {
            return shoppingListService.listByDate(LocalDate.parse(date));
        }
        return shoppingListService.todayList();
    }
}
