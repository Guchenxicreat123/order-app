package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.dto.CartAddReq;
import com.example.order.entity.Cart;
import com.example.order.service.CartService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired private CartService cartService;

    /** 加入购物车 */
    @PostMapping("/add")
    public Result<Cart> add(@RequestBody @Valid CartAddReq req) {
        return cartService.addToCart(req);
    }

    /** 当前用户购物车 */
    @GetMapping
    public Result<List<Cart>> list() {
        return cartService.listMyCart();
    }

    /** 删除购物车项 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        return cartService.removeFromCart(id);
    }

    /** 更新购物车数量（body: {quantity}，<=0 自动删除） */
    @PutMapping("/{id}")
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer quantity = body.get("quantity") == null ? null
                : Integer.valueOf(body.get("quantity").toString());
        return cartService.updateQuantity(id, quantity);
    }

    /** 批量下单 */
    @PostMapping("/checkout")
    public Result<List<Map<String, Object>>> checkout(@RequestBody Map<String, Object> body) {
        // 兼容 JSON 反序列化（Jackson 默认将数组元素序列化为 Integer）
        List<Long> cartIds = new ArrayList<>();
        Object raw = body.get("cartIds");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Number) cartIds.add(((Number) o).longValue());
            }
        }
        return cartService.checkout(cartIds);
    }
}