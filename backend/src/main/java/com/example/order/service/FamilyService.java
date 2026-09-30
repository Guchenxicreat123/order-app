package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.Family;
import com.example.order.entity.FamilyMember;
import com.example.order.entity.User;
import com.example.order.mapper.FamilyMapper;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FamilyService {

    @Autowired private FamilyMapper familyMapper;
    @Autowired private FamilyMemberMapper memberMapper;
    @Autowired private UserMapper userMapper;

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS[RNG.nextInt(CODE_CHARS.length)]);
        // 唯一性检查
        long cnt = familyMapper.selectCount(new LambdaQueryWrapper<Family>().eq(Family::getCode, sb.toString()));
        return cnt == 0 ? sb.toString() : generateCode();
    }

    /** 当前用户所在的家庭列表（含 role/code） */
    public Result<List<Map<String, Object>>> myFamilies() {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        List<Map<String, Object>> list = new ArrayList<>();
        List<FamilyMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId));
        if (memberships.isEmpty()) return Result.ok(list);
        List<Long> familyIds = memberships.stream().map(FamilyMember::getFamilyId).collect(Collectors.toList());
        List<Family> families = familyMapper.selectBatchIds(familyIds);
        Map<Long, Family> fmap = families.stream().collect(Collectors.toMap(Family::getId, f -> f));
        User user = userMapper.selectById(userId);
        for (FamilyMember m : memberships) {
            Family f = fmap.get(m.getFamilyId());
            if (f == null) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put("familyId", f.getId());
            entry.put("name", f.getName());
            entry.put("code", f.getCode());
            entry.put("role", m.getRole());
            entry.put("isOwner", "OWNER".equals(m.getRole()));
            entry.put("joinedAt", m.getJoinedAt());
            if (user != null && f.getId().equals(user.getActiveFamilyId())) entry.put("active", true);
            list.add(entry);
        }
        return Result.ok(list);
    }

    /** 创建家庭 */
    @Transactional
    public Result<Map<String, Object>> create(String name) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (name == null || name.isBlank()) return Result.error(400, "家庭名称不能为空");
        if (name.length() > 32) return Result.error(400, "家庭名称太长");
        Family f = new Family();
        f.setName(name.trim());
        f.setCode(generateCode());
        f.setOwnerUserId(userId);
        f.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(f);
        // 添加 OWNER 成员
        FamilyMember owner = new FamilyMember();
        owner.setFamilyId(f.getId());
        owner.setUserId(userId);
        owner.setRole("OWNER");
        User u = userMapper.selectById(userId);
        owner.setNickname(u != null ? u.getNickname() : null);
        owner.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(owner);
        // 设为活跃家庭
        User userUpd = new User();
        userUpd.setOpenId(userId);
        userUpd.setActiveFamilyId(f.getId());
        userMapper.updateById(userUpd);
        Map<String, Object> data = new HashMap<>();
        data.put("familyId", f.getId());
        data.put("name", f.getName());
        data.put("code", f.getCode());
        data.put("role", "OWNER");
        data.put("active", true);
        return Result.ok(data);
    }

    /** 加入家庭（用 6 位 code） */
    @Transactional
    public Result<Map<String, Object>> join(String code) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (code == null || code.isBlank()) return Result.error(400, "请输入加入码");
        String codeUp = code.trim().toUpperCase();
        Family f = familyMapper.selectOne(new LambdaQueryWrapper<Family>().eq(Family::getCode, codeUp));
        if (f == null) return Result.error(404, "加入码无效");
        long existing = memberMapper.selectCount(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, f.getId())
                        .eq(FamilyMember::getUserId, userId));
        if (existing > 0) {
            // 已是成员，直接返回
            FamilyMember m = memberMapper.selectOne(
                    new LambdaQueryWrapper<FamilyMember>()
                            .eq(FamilyMember::getFamilyId, f.getId())
                            .eq(FamilyMember::getUserId, userId));
            Map<String, Object> data = new HashMap<>();
            data.put("familyId", f.getId());
            data.put("name", f.getName());
            data.put("code", f.getCode());
            data.put("role", m.getRole());
            return Result.ok(data);
        }
        FamilyMember m = new FamilyMember();
        m.setFamilyId(f.getId());
        m.setUserId(userId);
        m.setRole("MEMBER");
        User u = userMapper.selectById(userId);
        m.setNickname(u != null ? u.getNickname() : null);
        m.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(m);
        // 设为活跃家庭
        User userUpd = new User();
        userUpd.setOpenId(userId);
        userUpd.setActiveFamilyId(f.getId());
        userMapper.updateById(userUpd);
        Map<String, Object> data = new HashMap<>();
        data.put("familyId", f.getId());
        data.put("name", f.getName());
        data.put("code", f.getCode());
        data.put("role", "MEMBER");
        data.put("active", true);
        return Result.ok(data);
    }

    /** 切换活跃家庭 */
    public Result<Void> switchFamily(Long familyId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        // 校验成员
        FamilyMember m = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (m == null) return Result.error(403, "您不是该家庭成员");
        User u = new User();
        u.setOpenId(userId);
        u.setActiveFamilyId(familyId);
        userMapper.updateById(u);
        return Result.ok();
    }

    /** 退出家庭（OWNER 不能退出，需先转移） */
    @Transactional
    public Result<Void> leave(Long familyId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        FamilyMember m = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (m == null) return Result.error(404, "您不在该家庭");
        if ("OWNER".equals(m.getRole())) return Result.error(400, "创建者不能直接退出，请先转移所有权");
        memberMapper.deleteById(m.getId());
        // 如果当前活跃家庭是这个，清掉活跃家庭（设为另一个）
        User u = userMapper.selectById(userId);
        if (u != null && familyId.equals(u.getActiveFamilyId())) {
            List<FamilyMember> remaining = memberMapper.selectList(
                    new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId));
            User upd = new User();
            upd.setOpenId(userId);
            upd.setActiveFamilyId(remaining.isEmpty() ? null : remaining.get(0).getFamilyId());
            userMapper.updateById(upd);
        }
        return Result.ok();
    }

    /** 成员列表 */
    public Result<List<Map<String, Object>>> listMembers(Long familyId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        // 必须是该家庭成员才能查看
        FamilyMember me = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "无权查看该家庭成员");
        List<FamilyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, familyId));
        if (members.isEmpty()) return Result.ok(new ArrayList<>());
        List<String> uids = members.stream().map(FamilyMember::getUserId).collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(uids);
        Map<String, User> umap = users.stream().collect(Collectors.toMap(User::getOpenId, x -> x));
        List<Map<String, Object>> result = new ArrayList<>();
        for (FamilyMember m : members) {
            User u = umap.get(m.getUserId());
            Map<String, Object> e = new HashMap<>();
            e.put("userId", m.getUserId());
            e.put("role", m.getRole());
            e.put("nickname", m.getNickname() != null ? m.getNickname() :
                    (u != null ? u.getNickname() : ""));
            e.put("avatar", u != null ? u.getAvatarUrl() : null);
            e.put("isChef", u != null && u.getIsChef() != null && u.getIsChef() == 1);
            e.put("joinedAt", m.getJoinedAt());
            result.add(e);
        }
        return Result.ok(result);
    }

    /** 认领主厨（仅当家庭没有主厨时允许；已有主厨时必须先退出） */
    @Transactional
    public Result<Map<String, Object>> claimChef(Long familyId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        // 必须是该家庭成员
        FamilyMember me = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "您不是该家庭成员");
        // 找当前主厨
        FamilyMember oldChef = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getRole, "CHEF"));
        // 已有主厨（且不是自己）→ 直接拒绝，不允许顶号
        if (oldChef != null && !userId.equals(oldChef.getUserId())) {
            return Result.error(409, "已有主厨，请等待其退出后再认领");
        }
        // 没有主厨（或就是自己）→ 设置自己为 CHEF
        me.setRole("CHEF");
        memberMapper.updateById(me);
        // 同步更新 user.is_chef 全局标识（保持兼容）
        User u = userMapper.selectById(userId);
        if (u != null) {
            User upd = new User();
            upd.setOpenId(userId);
            upd.setIsChef(1);
            userMapper.updateById(upd);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("familyId", familyId);
        data.put("newChefUserId", userId);
        data.put("newChefNickname", me.getNickname());
        return Result.ok(data);
    }

    /** 退出主厨（恢复 MEMBER） */
    @Transactional
    public Result<Void> resignChef(Long familyId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        FamilyMember me = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "您不是该家庭成员");
        if (!"CHEF".equals(me.getRole())) return Result.error(400, "您不是主厨");
        me.setRole("MEMBER");
        memberMapper.updateById(me);
        // 清掉 user.is_chef
        User upd = new User();
        upd.setOpenId(userId);
        upd.setIsChef(0);
        userMapper.updateById(upd);
        return Result.ok();
    }

    /** 踢人（仅 OWNER） */
    @Transactional
    public Result<Void> kick(Long familyId, String targetUserId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        FamilyMember me = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, userId));
        if (me == null || !"OWNER".equals(me.getRole())) return Result.error(403, "仅创建者可踢人");
        if (targetUserId.equals(userId)) return Result.error(400, "不能踢自己");
        FamilyMember target = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getUserId, targetUserId));
        if (target == null) return Result.error(404, "该用户不在家庭中");
        memberMapper.deleteById(target.getId());
        // 如果被踢的是当前家庭的主厨，清掉 user.is_chef
        if ("CHEF".equals(target.getRole())) {
            User upd = new User();
            upd.setOpenId(targetUserId);
            upd.setIsChef(0);
            userMapper.updateById(upd);
        }
        return Result.ok();
    }

    /** 获取当前家庭的主厨（按 familyId） */
    public Result<Map<String, Object>> chefStatus(Long familyId) {
        if (familyId == null) return Result.ok(new HashMap<>());
        FamilyMember chef = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, familyId)
                        .eq(FamilyMember::getRole, "CHEF"));
        Map<String, Object> data = new HashMap<>();
        data.put("hasChef", chef != null);
        if (chef != null) {
            User u = userMapper.selectById(chef.getUserId());
            data.put("chefUserId", chef.getUserId());
            data.put("chefNickname", chef.getNickname() != null ? chef.getNickname() :
                    (u != null ? u.getNickname() : ""));
        }
        return Result.ok(data);
    }

    /** 登录辅助：返回 (families, activeFamilyId)。新用户不自动加入任何家庭，由前端引导创建/加入 */
    @Transactional
    public Map<String, Object> loginFamilies(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<FamilyMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId));
        if (!memberships.isEmpty()) {
            List<Long> familyIds = memberships.stream().map(FamilyMember::getFamilyId).collect(Collectors.toList());
            Map<Long, Family> fmap = familyMapper.selectBatchIds(familyIds).stream()
                    .collect(Collectors.toMap(Family::getId, f -> f));
            User fresh = userMapper.selectById(userId);
            for (FamilyMember m : memberships) {
                Family f = fmap.get(m.getFamilyId());
                if (f == null) continue;
                Map<String, Object> entry = new HashMap<>();
                entry.put("familyId", f.getId());
                entry.put("name", f.getName());
                entry.put("code", f.getCode());
                entry.put("role", m.getRole());
                entry.put("isOwner", "OWNER".equals(m.getRole()));
                if (fresh != null && f.getId().equals(fresh.getActiveFamilyId())) entry.put("active", true);
                list.add(entry);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("families", list);
        User fresh2 = userMapper.selectById(userId);
        result.put("activeFamilyId", fresh2 != null ? fresh2.getActiveFamilyId() : null);
        return result;
    }
}