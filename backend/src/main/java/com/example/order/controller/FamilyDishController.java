package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.FamilyMember;
import com.example.order.mapper.FamilyMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.service.PublicDishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/family/dishes")
public class FamilyDishController {

    @Autowired private PublicDishService publicDishService;
    @Autowired private FamilyMemberMapper familyMemberMapper;

    /**
     * 把选中的公共菜品批量加入指定家庭菜单。
     * 小程序端：body: { familyId, publicDishIds }
     * 权限：只有该家庭的 OWNER 或 CHEF 才能操作。
     */
    @PostMapping("/from-public")
    public Result<?> addFromPublic(@RequestBody Map<String, Object> body) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");

        // familyId 优先从请求体取（小程序会传），其次从 header
        Long familyId = body.get("familyId") == null
                ? UserContext.getFamily()
                : Long.valueOf(body.get("familyId").toString());
        if (familyId == null) return Result.error(400, "请先选择或加入一个家庭");

        // 权限校验：必须是当前家庭的 OWNER 或 CHEF
        FamilyMember me = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "您不是该家庭成员");
        if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
            return Result.error(403, "只有创建者或主厨才能管理家庭菜单");
        }

        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("publicDishIds");
        return publicDishService.addToFamily(familyId, ids);
    }
}
