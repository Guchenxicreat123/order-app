package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.FamilyMember;
import com.example.order.service.AdminUserService;
import com.example.order.service.PublicDishService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.mapper.FamilyMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端接口（JwtInterceptor 限定主厨 token：/api/admin/*）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private AdminUserService adminUserService;
    @Autowired private PublicDishService publicDishService;
    @Autowired private FamilyMemberMapper familyMemberMapper;

    /** 全部用户（含所属家庭），按家庭排序 */
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listUsers() {
        return adminUserService.listUsers();
    }

    /** 删除用户（级联清理关联数据） */
    @DeleteMapping("/users/{userId}")
    public Result<Void> deleteUser(@PathVariable String userId) {
        return adminUserService.deleteUser(userId);
    }

    /** 全部家庭 */
    @GetMapping("/families")
    public Result<List<Map<String, Object>>> listFamilies() {
        return adminUserService.listFamilies();
    }

    /** 家庭一览（含创建者、主厨、成员） */
    @GetMapping("/families/overview")
    public Result<List<Map<String, Object>>> listFamiliesOverview() {
        return adminUserService.listFamiliesOverview();
    }

    /** 创建家庭（body: {name, ownerUserId?}） */
    @PostMapping("/families")
    public Result<Map<String, Object>> createFamily(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : body.get("name").toString();
        String ownerUserId = body.get("ownerUserId") == null ? null
                : body.get("ownerUserId").toString();
        return adminUserService.createFamily(name, ownerUserId);
    }

    /** 更新家庭名称（body: {name}） */
    @PutMapping("/families/{familyId}")
    public Result<Void> updateFamily(@PathVariable Long familyId, @RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : body.get("name").toString();
        return adminUserService.updateFamily(familyId, name);
    }

    /** 删除家庭 */
    @DeleteMapping("/families/{familyId}")
    public Result<Void> deleteFamily(@PathVariable Long familyId) {
        return adminUserService.deleteFamily(familyId);
    }

    /** 把用户加入家庭（body: {familyId, role?}） */
    @PostMapping("/users/{userId}/add-family")
    public Result<Map<String, Object>> addFamily(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        String role = body.get("role") == null ? null : body.get("role").toString();
        return adminUserService.addFamily(userId, familyId, role);
    }

    /** 把用户移出家庭（body: {familyId}） */
    @PostMapping("/users/{userId}/remove-family")
    public Result<Void> removeFamily(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        return adminUserService.removeFamily(userId, familyId);
    }

    /** 调整用户在家庭中的角色（body: {familyId, role}，role=MEMBER/CHEF） */
    @PostMapping("/users/{userId}/set-role")
    public Result<Map<String, Object>> setRole(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        String role = body.get("role") == null ? null : body.get("role").toString();
        return adminUserService.setRole(userId, familyId, role);
    }

    /** 管理员把公共菜品批量加入指定家庭（body: {familyId, publicDishIds}） */
    @PostMapping("/public-dishes/to-family")
    public Result<?> addPublicDishesToFamily(@RequestBody Map<String, Object> body) {
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        @SuppressWarnings("unchecked")
        List<Long> publicDishIds = (List<Long>) body.get("publicDishIds");
        if (familyId == null || publicDishIds == null) {
            return Result.error(400, "参数不完整");
        }
        // 权限校验：admin 必须是该家庭的 OWNER 或 CHEF
        String adminId = UserContext.get();
        FamilyMember me = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, adminId));
        if (me == null) return Result.error(403, "您不是该家庭成员");
        if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
            return Result.error(403, "只有创建者或主厨才能操作");
        }
        return publicDishService.addToFamily(familyId, publicDishIds);
    }

    /** 强制下线（撤销某用户全部 token）：递增其 token_version */
    @PostMapping("/users/{userId}/revoke")
    public Result<Void> revoke(@PathVariable String userId) {
        return adminUserService.revoke(userId);
    }
}
