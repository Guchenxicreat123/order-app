package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.dto.CartAddReq;
import com.example.order.entity.Cart;
import com.example.order.entity.Dish;
import com.example.order.entity.Ingredient;
import com.example.order.entity.User;
import lombok.extern.slf4j.Slf4j;
import com.example.order.mapper.CartMapper;
import com.example.order.mapper.DishMapper;
import com.example.order.mapper.IngredientMapper;
import com.example.order.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartService {

    @Autowired private CartMapper cartMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private IngredientMapper ingMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private DishService dishService;
    @Autowired private MenuItemService menuItemService;
    @Autowired private PushService pushService;
    @Autowired private ChefService chefService;

    /** 加入购物车（同一菜品数量累加） */
    @Transactional
    public Result<Cart> addToCart(CartAddReq req) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.error(400, "请先选择家庭");

        String dishName = req.getDishName();
        String dishEmoji;
        Long dishId = req.getDishId();
        BigDecimal price;
        final String reqCustomIngs = req.getCustomIngs();

        if (dishId != null) {
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null) return Result.error(404, "菜不存在");
            dishName = dish.getName();
            dishEmoji = dish.getImageEmoji() != null ? dish.getImageEmoji() : "";
            price = dishService.computeDishPrice(dishId);
        } else {
            if (dishName == null || dishName.isBlank()) dishName = "自定义菜";
            dishEmoji = "";
            price = computeCustomPrice(reqCustomIngs);
        }

        // 同用户同家庭同菜品（dishId 相同 或 同名自定义菜）→ 累加数量
        final Long finalDishId = dishId;
        final String finalDishName = dishName;
        LambdaQueryWrapper<Cart> q = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getFamilyId, familyId);
        if (finalDishId != null) {
            // 已有菜单：按 dishId 匹配
            q.eq(Cart::getDishId, finalDishId);
        } else {
            // 自定义菜：按 dishName 匹配（且 dish_id 为 NULL）
            q.isNull(Cart::getDishId)
                    .eq(Cart::getDishName, finalDishName);
        }
        Cart existing = cartMapper.selectOne(q);

        if (existing != null) {
            int addQty = req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1;
            existing.setQuantity(existing.getQuantity() + addQty);
            existing.setUpdatedAt(LocalDateTime.now());
            cartMapper.updateById(existing);
            return Result.ok(existing);
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setFamilyId(familyId);
        cart.setDishId(dishId);
        cart.setDishName(dishName);
        cart.setDishEmoji(dishEmoji);
        cart.setCustomIngs(dishId != null ? null : reqCustomIngs);
        cart.setSpiceLevel(req.getSpiceLevel() != null ? req.getSpiceLevel() : 0);
        cart.setRemark(req.getRemark());
        cart.setPrice(price);
        cart.setQuantity(req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1);
        cart.setCreatedAt(LocalDateTime.now());
        cartMapper.insert(cart);
        return Result.ok(cart);
    }

    /** 当前用户购物车列表 */
    public Result<List<Cart>> listMyCart() {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        List<Cart> list = cartMapper.selectList(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getFamilyId, familyId)
                        .orderByDesc(Cart::getCreatedAt));
        return Result.ok(list);
    }

    /** 根据 ID 查询（仅限本人） */
    public Cart findById(Long id) {
        if (id == null) return null;
        Cart cart = cartMapper.selectById(id);
        if (cart == null) return null;
        String userId = UserContext.get();
        if (userId == null || cart.getUserId() == null
                || !cart.getUserId().equals(userId)) return null;
        return cart;
    }

    /** 删除购物车单项（只能删自己的） */
    public Result<Void> removeFromCart(Long id) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        Cart cart = cartMapper.selectById(id);
        if (cart == null) return Result.error(404, "购物车项不存在");
        if (cart.getUserId() == null || !cart.getUserId().equals(userId)) return Result.error(403, "只能删除自己的购物车项");
        cartMapper.deleteById(id);
        return Result.ok();
    }

    /** 更新购物车数量（quantity <= 0 时删除） */
    @Transactional
    public Result<Void> updateQuantity(Long id, Integer quantity) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        Cart cart = cartMapper.selectById(id);
        if (cart == null) return Result.error(404, "购物车项不存在");
        if (cart.getUserId() == null || !cart.getUserId().equals(userId))
            return Result.error(403, "只能修改自己的购物车项");
        if (quantity == null || quantity <= 0) {
            cartMapper.deleteById(id);
        } else {
            cart.setQuantity(quantity);
            cart.setUpdatedAt(LocalDateTime.now());
            cartMapper.updateById(cart);
        }
        return Result.ok();
    }

    /** 批量下单：购物车项 → 创建菜单项 + 推送主厨 + 删除购物车项 */
    @Transactional
    public Result<List<Map<String, Object>>> checkout(List<Long> cartIds) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (cartIds == null || cartIds.isEmpty()) return Result.error(400, "请先选择要下单的菜品");

        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.error(400, "请先选择家庭");

        // 家庭必须有主厨才能下单
        if (!chefService.hasChef(familyId)) {
            return Result.error(400, "当前家庭还没有主厨，请先让家庭成员认领主厨再下单");
        }

        // 获取用户名
        String nickname = "";
        User user = userMapper.selectById(userId);
        if (user != null && user.getNickname() != null) nickname = user.getNickname();

        List<Map<String, Object>> results = new ArrayList<>();
        for (Long cartId : cartIds) {
            Cart cart = cartMapper.selectById(cartId);
            if (cart == null) continue;
            if (cart.getUserId() == null || !cart.getUserId().equals(userId)) continue;

            int qty = cart.getQuantity() != null ? cart.getQuantity() : 1;
            for (int i = 0; i < qty; i++) {
                Result<Map<String, Object>> r = menuItemService.addItem(
                        userId, nickname,
                        cart.getDishId(), cart.getDishName(), cart.getDishEmoji(),
                        cart.getCustomIngs(),
                        cart.getSpiceLevel(),
                        cart.getRemark());

                if (r.getCode() == 0) {
                    // 这里拿到的是**菜品 id**（t_menu_item.id），不是订单 id：
                    // 结算走的是「每道菜各自 addItem」，不建 t_order 记录（MenuService 的购物车模型）。
                    // 传错会把菜品 id 当订单 id 查，必然推不出去——必须用 pushNewOrderForItem。
                    Long itemId = (Long) r.getData().get("id");
                    pushService.pushAfterCommit(() -> pushService.pushNewOrderForItem(itemId));
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("menuItemId", itemId);
                    entry.put("cartId", cartId);
                    entry.put("dishName", cart.getDishName());
                    results.add(entry);
                }
            }
            // 删除购物车项（整条删除，数量已全部转为菜单项）
            cartMapper.deleteById(cartId);
        }
        if (results.isEmpty()) return Result.error(400, "下单失败，请重试");
        return Result.ok(results);
    }

    /** 计算自定义菜价格 */
    private BigDecimal computeCustomPrice(String customIngs) {
        if (customIngs == null || customIngs.isBlank()) return BigDecimal.ZERO;
        try {
            List<Map> list = new ObjectMapper().readValue(customIngs, List.class);
            if (list.isEmpty()) return BigDecimal.ZERO;
            Set<Long> ids = list.stream()
                    .map(m -> ((Number) m.get("ingId")).longValue())
                    .collect(Collectors.toSet());
            Map<Long, Ingredient> map = ingMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Ingredient::getId, i -> i));
            BigDecimal total = BigDecimal.ZERO;
            for (Map m : list) {
                Long ingId = ((Number) m.get("ingId")).longValue();
                BigDecimal amount = new BigDecimal(m.get("amount").toString());
                Ingredient ing = map.get(ingId);
                if (ing != null) total = total.add(ing.getPrice().multiply(amount));
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[Cart] 自定义菜价格计算失败，视为 0", e);
            return BigDecimal.ZERO;
        }
    }
}