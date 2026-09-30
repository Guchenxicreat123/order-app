package com.example.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.entity.Cart;
import com.example.order.entity.FamilyMember;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.service.CartService;
import com.example.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订单接口：用户下单后生成订单，可按订单维度查询与确认。
 * /api/orders 需要 USER token（小程序端）。
 * /api/orders/{id}/confirm 与 /api/orders/items/{id}/confirm 需要 CHEF/OWNER。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private FamilyMemberMapper memberMapper;

    /**
     * 购物车下单：传入 cartIds 列表，后端一次性创建一个订单，
     * 所有 cart 项转为 order 下的 menu items，购物车清空。
     */
    @PostMapping("/checkout")
    public Result<Map<String, Object>> checkout(@RequestBody Map<String, Object> body) {
        // 兼容 JSON 数字反序列化为 Integer
        List<Long> cartIds = new ArrayList<>();
        Object raw = body.get("cartIds");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Number) cartIds.add(((Number) o).longValue());
            }
        }
        String remark = body.get("remark") == null ? null : body.get("remark").toString();
        if (cartIds.isEmpty()) return Result.error(400, "请先选择菜品");

        // 取出这些 cart items（保持前端选中的顺序）
        List<Cart> items = new ArrayList<>();
        for (Long id : cartIds) {
            Cart c = cartService.findById(id);
            if (c == null) continue;
            items.add(c);
        }
        Result<Map<String, Object>> r = orderService.createOrderFromCart(items, remark);
        if (r.getCode() == 0) {
            // 成功后再删除 cart items
            for (Cart c : items) cartService.removeFromCart(c.getId());
        }
        return r;
    }

    /** 当前家庭全部订单 */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return orderService.listFamilyOrders();
    }

    /** 当前家庭今日订单 */
    @GetMapping("/today")
    public Result<List<Map<String, Object>>> today() {
        return orderService.listTodayOrders();
    }

    /** 订单详情 */
    @GetMapping("/{orderId}")
    public Result<Map<String, Object>> detail(@PathVariable Long orderId) {
        return orderService.getOrderDetail(orderId);
    }

    /** 一键确认整个订单（CHEF/OWNER） */
    @PostMapping("/{orderId}/confirm")
    public Result<Void> confirmOrder(@PathVariable Long orderId) {
        return orderService.confirmOrder(orderId);
    }

    /** 单菜确认（CHEF/OWNER） */
    @PostMapping("/items/{itemId}/confirm")
    public Result<Void> confirmItem(@PathVariable Long itemId) {
        return orderService.confirmItem(itemId);
    }

    /** 单菜驳回（CHEF/OWNER） */
    @PostMapping("/items/{itemId}/reject")
    public Result<Void> rejectItem(@PathVariable Long itemId) {
        return orderService.rejectItem(itemId);
    }

    /** 修改订单：status / remark（管理端） */
    @PutMapping("/{orderId}")
    public Result<Void> updateOrder(@PathVariable Long orderId, @RequestBody(required = false) Map<String, Object> body) {
        return orderService.updateOrder(orderId, body);
    }

    /** 删除订单（管理端，级联删除菜单项） */
    @DeleteMapping("/{orderId}")
    public Result<Void> deleteOrder(@PathVariable Long orderId) {
        return orderService.deleteOrder(orderId);
    }
}