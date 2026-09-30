package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.Cart;
import com.example.order.entity.Dish;
import com.example.order.entity.DishBlacklist;
import com.example.order.entity.DishIngredient;
import com.example.order.entity.Family;
import com.example.order.entity.FamilyMember;
import com.example.order.entity.Ingredient;
import com.example.order.entity.IngredientCategory;
import com.example.order.entity.MenuItem;
import com.example.order.entity.MenuItemRating;
import com.example.order.entity.Order;
import com.example.order.entity.PushLog;
import com.example.order.entity.PushPending;
import com.example.order.entity.User;
import com.example.order.mapper.CartMapper;
import com.example.order.mapper.DishBlacklistMapper;
import com.example.order.mapper.DishIngredientMapper;
import com.example.order.mapper.DishMapper;
import com.example.order.mapper.FamilyMapper;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.mapper.IngredientCategoryMapper;
import com.example.order.mapper.IngredientMapper;
import com.example.order.mapper.MenuItemMapper;
import com.example.order.mapper.MenuItemRatingMapper;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.PushLogMapper;
import com.example.order.mapper.PushPendingMapper;
import com.example.order.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端：用户与家庭的全局管理（仅主厨 token 可访问，见 JwtInterceptor）。
 * 一个用户可属于多个家庭；role: OWNER / CHEF / MEMBER。
 */
@Service
public class AdminUserService {

    @Autowired private UserMapper userMapper;
    @Autowired private FamilyMapper familyMapper;
    @Autowired private FamilyMemberMapper memberMapper;
    @Autowired private CartMapper cartMapper;
    @Autowired private MenuItemMapper menuItemMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private DishIngredientMapper dishIngredientMapper;
    @Autowired private DishBlacklistMapper dishBlacklistMapper;
    @Autowired private IngredientMapper ingredientMapper;
    @Autowired private IngredientCategoryMapper ingredientCategoryMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private MenuItemRatingMapper menuItemRatingMapper;
    @Autowired private PushLogMapper pushLogMapper;
    @Autowired private PushPendingMapper pushPendingMapper;

    private static final Collator CN = Collator.getInstance(Locale.CHINA);
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    // ==================== 用户列表 ====================

    /** 全部用户 + 所属家庭，按“主家庭名 + userId”排序（无家庭排最后） */
    public Result<List<Map<String, Object>>> listUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getOpenId));
        if (users.isEmpty()) return Result.ok(new ArrayList<>());

        // 一次取出全部家庭与成员关系
        List<Family> fams = familyMapper.selectList(new LambdaQueryWrapper<Family>().orderByAsc(Family::getId));
        Map<Long, Family> famMap = fams.stream().collect(Collectors.toMap(Family::getId, f -> f));
        Map<String, List<FamilyMember>> membersByUser = memberMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(FamilyMember::getUserId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", u.getOpenId());
            entry.put("nickname", u.getNickname());
            entry.put("avatarUrl", u.getAvatarUrl());
            entry.put("phone", u.getPhone());
            entry.put("isChef", u.getIsChef() != null && u.getIsChef() == 1);
            entry.put("allowPush", u.getAllowPush() != null && u.getAllowPush() == 1);
            entry.put("activeFamilyId", u.getActiveFamilyId());
            entry.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");

            List<FamilyMember> ms = membersByUser.getOrDefault(u.getOpenId(), new ArrayList<>());
            ms.sort(Comparator.comparing(m -> m.getJoinedAt() == null ? LocalDateTime.MIN : m.getJoinedAt()));
            List<Map<String, Object>> families = new ArrayList<>();
            for (FamilyMember m : ms) {
                Family f = famMap.get(m.getFamilyId());
                if (f == null) continue;
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("familyId", f.getId());
                fm.put("familyName", f.getName());
                fm.put("code", f.getCode());
                fm.put("role", m.getRole());
                fm.put("isOwner", "OWNER".equals(m.getRole()));
                fm.put("active", u.getActiveFamilyId() != null && u.getActiveFamilyId().equals(f.getId()));
                families.add(fm);
            }
            // 主家庭：优先活跃家庭，其次最早加入的家庭
            String primary = "";
            Map<String, Object> activeFam = families.stream().filter(x -> Boolean.TRUE.equals(x.get("active")))
                    .findFirst().orElse(null);
            if (activeFam != null) {
                primary = String.valueOf(activeFam.get("familyName"));
            } else if (!families.isEmpty()) {
                primary = String.valueOf(families.get(0).get("familyName"));
            }
            entry.put("families", families);
            entry.put("primaryFamilyName", primary);
            result.add(entry);
        }

        // 按主家庭名（中文拼音）排序；无家庭排最后；同家庭按 userId
        result.sort((a, b) -> {
            String pa = (String) a.get("primaryFamilyName");
            String pb = (String) b.get("primaryFamilyName");
            if (pa.isEmpty() && pb.isEmpty()) return ((String) a.get("userId")).compareTo((String) b.get("userId"));
            if (pa.isEmpty()) return 1;
            if (pb.isEmpty()) return -1;
            int c = CN.compare(pa, pb);
            if (c != 0) return c;
            return ((String) a.get("userId")).compareTo((String) b.get("userId"));
        });
        return Result.ok(result);
    }

    /** 全部家庭（供下拉选择） */
    public Result<List<Map<String, Object>>> listFamilies() {
        List<Family> fams = familyMapper.selectList(new LambdaQueryWrapper<Family>().orderByAsc(Family::getId));
        List<Long> ids = fams.stream().map(Family::getId).collect(Collectors.toList());
        Map<Long, Long> memberCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            memberMapper.selectList(new LambdaQueryWrapper<FamilyMember>().in(FamilyMember::getFamilyId, ids))
                    .stream().collect(Collectors.groupingBy(FamilyMember::getFamilyId, Collectors.counting()))
                    .forEach(memberCounts::put);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Family f : fams) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("familyId", f.getId());
            e.put("name", f.getName());
            e.put("code", f.getCode());
            e.put("memberCount", memberCounts.getOrDefault(f.getId(), 0L));
            result.add(e);
        }
        return Result.ok(result);
    }

    /** 家庭一览表：每个家庭含创建者、主厨、成员列表 */
    public Result<List<Map<String, Object>>> listFamiliesOverview() {
        List<Family> fams = familyMapper.selectList(new LambdaQueryWrapper<Family>().orderByAsc(Family::getId));
        if (fams.isEmpty()) return Result.ok(new ArrayList<>());

        List<Long> ids = fams.stream().map(Family::getId).collect(Collectors.toList());
        Map<Long, List<FamilyMember>> membersByFamily = memberMapper.selectList(
                        new LambdaQueryWrapper<FamilyMember>().in(FamilyMember::getFamilyId, ids))
                .stream().collect(Collectors.groupingBy(FamilyMember::getFamilyId));

        // 用户昵称（按 userId 汇总一次）
        Map<String, User> userMap = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getOpenId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Family f : fams) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("familyId", f.getId());
            e.put("name", f.getName());
            e.put("code", f.getCode());
            e.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "");

            List<FamilyMember> ms = membersByFamily.getOrDefault(f.getId(), new ArrayList<>());
            ms.sort(Comparator.comparing(m -> m.getJoinedAt() == null ? LocalDateTime.MIN : m.getJoinedAt()));

            String ownerName = "", chefName = "";
            List<Map<String, Object>> members = new ArrayList<>();
            for (FamilyMember m : ms) {
                User u = userMap.get(m.getUserId());
                String nickname = m.getNickname() != null && !m.getNickname().isBlank()
                        ? m.getNickname() : (u != null ? u.getNickname() : ("用户#" + m.getUserId()));
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("userId", m.getUserId());
                mm.put("nickname", nickname);
                mm.put("role", m.getRole());
                mm.put("joinedAt", m.getJoinedAt() != null ? m.getJoinedAt().toString() : "");
                members.add(mm);

                if ("OWNER".equals(m.getRole()) && ownerName.isEmpty()) ownerName = nickname;
                if ("CHEF".equals(m.getRole()) && chefName.isEmpty()) chefName = nickname;
            }
            e.put("ownerName", ownerName);
            e.put("chefName", chefName);
            e.put("memberCount", ms.size());
            e.put("members", members);
            result.add(e);
        }
        return Result.ok(result);
    }

    // ==================== 删除用户 ====================

    /**
     * 删除用户（级联清理关联数据，顺序按外键依赖从子到父）。
     * 顺序：t_push_pending → t_push_log → t_menu_item_rating → t_menu_item
     *      → t_order → t_dish_blacklist → t_cart / t_family_member → t_user。
     *
     * 例外：该用户若是某个家庭的创建者（t_family.owner_user_id 指向他），
     * 直接删会连带毁掉整个家庭（含其他成员的订单），所以这里拒绝并要求
     * 先在「家庭管理」里处理该家庭，避免静默破坏他人数据。
     */
    @Transactional
    public Result<Void> deleteUser(String userId) {
        if (userId == null) return Result.error(400, "userId 不能为空");
        User u = userMapper.selectById(userId);
        if (u == null) return Result.error(404, "用户不存在");

        // 防止误删自己
        String adminId = UserContext.get();
        if (adminId != null && userId.equals(adminId)) {
            return Result.error(400, "不能删除自己");
        }

        // 该用户是家庭创建者 → 拒绝（删他等于删整个家庭，影响其他成员）
        List<Family> owned = familyMapper.selectList(
                new LambdaQueryWrapper<Family>().eq(Family::getOwnerUserId, userId));
        if (!owned.isEmpty()) {
            String names = owned.stream().map(Family::getName).collect(Collectors.joining("、"));
            return Result.error(400,
                    "该用户是家庭「" + names + "」的创建者，请在家庭管理中先删除或转移该家庭");
        }

        // 1. 推送待发（无子表，先删）
        pushPendingMapper.delete(new LambdaQueryWrapper<PushPending>().eq(PushPending::getUserId, userId));

        // 2. 推送日志（无子表）
        pushLogMapper.delete(new LambdaQueryWrapper<PushLog>().eq(PushLog::getUserId, userId));

        // 3. 菜单项评分：先删「评分人是该用户」的，再删「评的是该用户菜单项」的
        menuItemRatingMapper.delete(new LambdaQueryWrapper<MenuItemRating>()
                .eq(MenuItemRating::getUserId, userId));
        List<MenuItem> userMenuItems = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getUserId, userId));
        if (!userMenuItems.isEmpty()) {
            List<Long> itemIds = userMenuItems.stream().map(MenuItem::getId).collect(Collectors.toList());
            menuItemRatingMapper.delete(new LambdaQueryWrapper<MenuItemRating>()
                    .in(MenuItemRating::getMenuItemId, itemIds));
        }

        // 4. 菜单项
        menuItemMapper.delete(new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getUserId, userId));

        // 5. 订单（t_order_item 表不存在，无子表）
        orderMapper.delete(new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId));

        // 6. 菜品黑名单
        dishBlacklistMapper.delete(new LambdaQueryWrapper<DishBlacklist>().eq(DishBlacklist::getUserId, userId));

        // 7. 家庭成员关系
        memberMapper.delete(new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId));

        // 8. 购物车
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));

        // 9. 删除用户本体
        userMapper.deleteById(userId);
        return Result.ok();
    }

    // ==================== 调整家庭归属 ====================

    /** 把用户加入家庭（已是成员则更新角色）。role: MEMBER / CHEF，默认 MEMBER */
    @Transactional
    public Result<Map<String, Object>> addFamily(String userId, Long familyId, String role) {
        if (userId == null || familyId == null) return Result.error(400, "参数不完整");
        User u = userMapper.selectById(userId);
        if (u == null) return Result.error(404, "用户不存在");
        Family f = familyMapper.selectById(familyId);
        if (f == null) return Result.error(404, "家庭不存在");

        String targetRole = normalizeRole(role);
        if (targetRole == null) return Result.error(400, "角色只能是 MEMBER 或 CHEF");

        FamilyMember existing = findMember(familyId, userId);
        if (existing != null) {
            return changeRole(familyId, userId, targetRole, false);
        }

        FamilyMember m = new FamilyMember();
        m.setFamilyId(familyId);
        m.setUserId(userId);
        m.setRole(targetRole);
        m.setNickname(u.getNickname());
        m.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(m);

        if ("CHEF".equals(targetRole)) {
            demoteOtherChef(familyId, userId);
        }
        // 用户还没有活跃家庭时，把新加入的家庭设为活跃
        if (u.getActiveFamilyId() == null) {
            User upd = new User();
            upd.setOpenId(userId);
            upd.setActiveFamilyId(familyId);
            userMapper.updateById(upd);
        }
        syncGlobalChef(userId);
        return Result.ok(Map.of("familyId", familyId, "role", targetRole));
    }

    /** 把用户移出家庭（禁止移出 OWNER） */
    @Transactional
    public Result<Void> removeFamily(String userId, Long familyId) {
        if (userId == null || familyId == null) return Result.error(400, "参数不完整");
        FamilyMember m = findMember(familyId, userId);
        if (m == null) return Result.error(404, "该用户不在该家庭");
        if ("OWNER".equals(m.getRole())) return Result.error(400, "创建者不能移出，请先转移所有权");

        boolean wasChef = "CHEF".equals(m.getRole());
        memberMapper.deleteById(m.getId());

        if (wasChef) syncGlobalChef(userId);

        // 活跃家庭被移出 → 指向剩余的第一个家庭
        User u = userMapper.selectById(userId);
        if (u != null && familyId.equals(u.getActiveFamilyId())) {
            List<FamilyMember> remaining = memberMapper.selectList(
                    new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId)
                            .orderByAsc(FamilyMember::getJoinedAt));
            User upd = new User();
            upd.setOpenId(userId);
            upd.setActiveFamilyId(remaining.isEmpty() ? null : remaining.get(0).getFamilyId());
            userMapper.updateById(upd);
        }
        return Result.ok();
    }

    /** 调整某用户在家庭中的角色（MEMBER / CHEF） */
    @Transactional
    public Result<Map<String, Object>> setRole(String userId, Long familyId, String role) {
        if (userId == null || familyId == null) return Result.error(400, "参数不完整");
        String targetRole = normalizeRole(role);
        if (targetRole == null) return Result.error(400, "角色只能是 MEMBER 或 CHEF");
        return changeRole(familyId, userId, targetRole, true);
    }

    // ==================== 内部工具 ====================

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "MEMBER";
        String r = role.trim().toUpperCase();
        return ("MEMBER".equals(r) || "CHEF".equals(r)) ? r : null;
    }

    private FamilyMember findMember(Long familyId, String userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId)
                .eq(FamilyMember::getUserId, userId));
    }

    /** 成员已存在时更新角色（OWNER 不可改） */
    private Result<Map<String, Object>> changeRole(Long familyId, String userId, String targetRole, boolean requireMember) {
        FamilyMember m = findMember(familyId, userId);
        if (m == null) {
            if (requireMember) return Result.error(404, "该用户不在该家庭");
            return addFamily(userId, familyId, targetRole).getCode() == 0
                    ? Result.ok(Map.of("familyId", familyId, "role", targetRole))
                    : Result.error("操作失败");
        }
        if ("OWNER".equals(m.getRole()) && !"OWNER".equals(targetRole)) {
            return Result.error(400, "创建者角色不可修改");
        }
        if (m.getRole().equals(targetRole)) {
            return Result.ok(Map.of("familyId", familyId, "role", targetRole));
        }
        m.setRole(targetRole);
        memberMapper.updateById(m);
        if ("CHEF".equals(targetRole)) {
            demoteOtherChef(familyId, userId);
        }
        syncGlobalChef(userId);
        return Result.ok(Map.of("familyId", familyId, "role", targetRole));
    }

    /** 家庭只能有一个主厨：把其它 CHEF 降为 MEMBER */
    private void demoteOtherChef(Long familyId, String keepUserId) {
        List<FamilyMember> chefs = memberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId)
                .eq(FamilyMember::getRole, "CHEF")
                .ne(FamilyMember::getUserId, keepUserId));
        for (FamilyMember c : chefs) {
            c.setRole("MEMBER");
            memberMapper.updateById(c);
            syncGlobalChef(c.getUserId());
        }
    }

    /** 同步 user.is_chef 全局镜像：只要用户在任一家庭是 CHEF 即 1 */
    private void syncGlobalChef(String userId) {
        if (userId == null) return;
        Long cnt = memberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId)
                .eq(FamilyMember::getRole, "CHEF"));
        User upd = new User();
        upd.setOpenId(userId);
        upd.setIsChef(cnt != null && cnt > 0 ? 1 : 0);
        userMapper.updateById(upd);
    }

    // ==================== 家庭增删改 ====================

    /** 生成唯一 6 位加入码 */
    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS[RNG.nextInt(CODE_CHARS.length)]);
        long cnt = familyMapper.selectCount(new LambdaQueryWrapper<Family>().eq(Family::getCode, sb.toString()));
        return cnt == 0 ? sb.toString() : generateCode();
    }

    /**
     * 创建家庭（admin 面板）。
     * @param name 家庭名称
     * @param ownerUserId 创建者用户 ID（null 则使用 admin 自己）
     * @return familyId, name, code
     */
    @Transactional
    public Result<Map<String, Object>> createFamily(String name, String ownerUserId) {
        if (name == null || name.isBlank()) return Result.error(400, "家庭名称不能为空");
        if (name.length() > 32) return Result.error(400, "名称不能超过 32 字符");
        String userId = ownerUserId != null ? ownerUserId : UserContext.get();
        if (userId == null) return Result.error(401, "未登录");
        User owner = userMapper.selectById(userId);
        if (owner == null) return Result.error(404, "用户不存在");

        Family f = new Family();
        f.setName(name.trim());
        f.setCode(generateCode());
        f.setOwnerUserId(userId);
        f.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(f);

        // 全局管理员（is_chef=1，即后台 admin）创建家庭时，不作为家庭成员加入，
        // 由后续业务单独把真实用户加入家庭。
        boolean isGlobalAdmin = owner.getIsChef() != null && owner.getIsChef() == 1;
        if (!isGlobalAdmin) {
            FamilyMember ownerMember = new FamilyMember();
            ownerMember.setFamilyId(f.getId());
            ownerMember.setUserId(userId);
            ownerMember.setRole("OWNER");
            ownerMember.setNickname(owner.getNickname());
            ownerMember.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(ownerMember);

            // 如果该用户还没有活跃家庭，设为这个
            if (owner.getActiveFamilyId() == null) {
                User u2 = new User();
                u2.setOpenId(userId);
                u2.setActiveFamilyId(f.getId());
                userMapper.updateById(u2);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("familyId", f.getId());
        data.put("name", f.getName());
        data.put("code", f.getCode());
        return Result.ok(data);
    }

    /**
     * 更新家庭名称。
     */
    public Result<Void> updateFamily(Long familyId, String name) {
        if (familyId == null) return Result.error(400, "familyId 不能为空");
        if (name == null || name.isBlank()) return Result.error(400, "家庭名称不能为空");
        if (name.length() > 32) return Result.error(400, "名称不能超过 32 字符");
        Family f = familyMapper.selectById(familyId);
        if (f == null) return Result.error(404, "家庭不存在");
        f.setName(name.trim());
        familyMapper.updateById(f);
        return Result.ok();
    }

    /**
     * 删除家庭（级联清理全部关联数据）。
     */
    @Transactional
    public Result<Void> deleteFamily(Long familyId) {
        if (familyId == null) return Result.error(400, "familyId 不能为空");
        Family f = familyMapper.selectById(familyId);
        if (f == null) return Result.error(404, "家庭不存在");

        // 1. 收集待清理的 user 与 dish
        List<FamilyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, familyId));
        List<String> memberUserIds = members.stream().map(FamilyMember::getUserId).collect(Collectors.toList());

        List<Dish> dishes = dishMapper.selectList(
                new LambdaQueryWrapper<Dish>().eq(Dish::getFamilyId, familyId));
        List<Long> dishIds = dishes.stream().map(Dish::getId).collect(Collectors.toList());

        // 2. 删 dish_ingredient（按 familyId 或 dishId）
        if (!dishIds.isEmpty()) {
            dishIngredientMapper.delete(new LambdaQueryWrapper<DishIngredient>()
                    .in(DishIngredient::getDishId, dishIds));
            dishBlacklistMapper.delete(new LambdaQueryWrapper<DishBlacklist>()
                    .in(DishBlacklist::getDishId, dishIds));
        }
        dishIngredientMapper.delete(new LambdaQueryWrapper<DishIngredient>()
                .eq(DishIngredient::getFamilyId, familyId));

        // 3. 删 dish
        dishMapper.delete(new LambdaQueryWrapper<Dish>().eq(Dish::getFamilyId, familyId));

        // 4. 删 menu_item
        menuItemMapper.delete(new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getFamilyId, familyId));

        // 5. 删 cart
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getFamilyId, familyId));

        // 6. 删 ingredients & categories
        ingredientMapper.delete(new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getFamilyId, familyId));
        ingredientCategoryMapper.delete(new LambdaQueryWrapper<IngredientCategory>().eq(IngredientCategory::getFamilyId, familyId));

        // 7. 删家庭成员
        memberMapper.delete(new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, familyId));

        // 8. 修复被删除成员的 activeFamilyId 与 isChef
        for (String uid : memberUserIds) {
            // 重新计算 activeFamilyId：取该用户剩余最早的家庭，没有则设为 null
            List<FamilyMember> remaining = memberMapper.selectList(
                    new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, uid)
                            .orderByAsc(FamilyMember::getJoinedAt));
            Long newActive = remaining.isEmpty() ? null : remaining.get(0).getFamilyId();
            User u = userMapper.selectById(uid);
            if (u != null) {
                if (!Objects.equals(u.getActiveFamilyId(), newActive)) {
                    User u2 = new User();
                    u2.setOpenId(uid);
                    u2.setActiveFamilyId(newActive);
                    userMapper.updateById(u2);
                }
                // 同步 isChef：该用户在任意家庭是否还是 CHEF
                syncGlobalChef(uid);
            }
        }

        // 9. 删家庭本体
        familyMapper.deleteById(familyId);
        return Result.ok();
    }

    /**
     * 强制下线：递增指定用户的 token_version，所有已签发 token 立即作废。
     * 用于管理后台"踢人"、发现可疑登录后的应急撤销。
     */
    public Result<Void> revoke(String openId) {
        if (openId == null || openId.isBlank()) return Result.error(400, "userId 不能为空");
        User u = userMapper.selectById(openId);
        if (u == null) return Result.error(404, "用户不存在");
        int cur = u.getTokenVersion() == null ? 0 : u.getTokenVersion();
        u.setTokenVersion(cur + 1);
        userMapper.updateById(u);
        return Result.ok();
    }
}
