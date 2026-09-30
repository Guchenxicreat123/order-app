package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.User;
import com.example.order.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 主厨服务：查询主厨状态 / 活跃家庭主厨判定 */
@Service
public class ChefService {

    @Autowired private UserMapper userMapper;
    @Autowired private com.example.order.mapper.FamilyMemberMapper familyMemberMapper;

    /** 查询当前用户的主厨状态（按当前活跃家庭） */
    public Result<Map<String, Object>> chefStatus(Long familyId) {
        String userId = UserContext.get();

        User chef = null;
        if (familyId != null) {
            com.example.order.entity.FamilyMember chefMember = familyMemberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                            .eq(com.example.order.entity.FamilyMember::getFamilyId, familyId)
                            .eq(com.example.order.entity.FamilyMember::getRole, "CHEF"));
            if (chefMember != null) {
                chef = userMapper.selectById(chefMember.getUserId());
            }
        } else {
            // 回退：全局查询
            chef = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getIsChef, 1));
        }

        boolean hasChef = chef != null;
        boolean isMe = (userId != null && chef != null && chef.getOpenId().equals(userId));

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("hasChef", hasChef);
        result.put("chefId", hasChef ? chef.getOpenId() : null);
        result.put("chefNickname", hasChef && chef.getNickname() != null ? chef.getNickname() : "");
        result.put("isMe", isMe);
        return Result.ok(result);
    }

    /** 判断某家庭是否已有主厨 */
    public boolean hasChef(Long familyId) {
        if (familyId == null) return false;
        Long cnt = familyMemberMapper.selectCount(
                new LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                        .eq(com.example.order.entity.FamilyMember::getFamilyId, familyId)
                        .eq(com.example.order.entity.FamilyMember::getRole, "CHEF"));
        return cnt != null && cnt > 0;
    }

    /** 判断用户是否为其活跃家庭的主厨（内部用） */
    public boolean isChef(String userId) {
        if (userId == null) return false;
        Long familyId = UserContext.getFamily();
        if (familyId != null) {
            var m = familyMemberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                            .eq(com.example.order.entity.FamilyMember::getFamilyId, familyId)
                            .eq(com.example.order.entity.FamilyMember::getUserId, userId)
                            .eq(com.example.order.entity.FamilyMember::getRole, "CHEF"));
            if (m != null) return true;
        }
        // 回退：全局主厨
        User u = userMapper.selectById(userId);
        return u != null && u.getIsChef() != null && u.getIsChef() == 1;
    }
}
