package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.dto.FamilyCreateReq;
import com.example.order.dto.FamilyJoinReq;
import com.example.order.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/family")
public class FamilyController {

    @Autowired private FamilyService familyService;

    /** 我的家庭列表 */
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> myFamilies() {
        return familyService.myFamilies();
    }

    /** 创建家庭 */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody @Valid FamilyCreateReq req) {
        return familyService.create(req.getName());
    }

    /** 加入家庭（用 6 位 code） */
    @PostMapping("/join")
    public Result<Map<String, Object>> join(@RequestBody @Valid FamilyJoinReq req) {
        return familyService.join(req.getCode());
    }

    /** 切换活跃家庭 */
    @PostMapping("/switch")
    public Result<Void> switchFamily(@RequestBody Map<String, Long> body) {
        return familyService.switchFamily(body.get("familyId"));
    }

    /** 退出家庭 */
    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        return familyService.leave(id);
    }

    /** 成员列表 */
    @GetMapping("/{id}/members")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id) {
        return familyService.listMembers(id);
    }

    /** 认领/接任主厨 */
    @PostMapping("/{id}/claim-chef")
    public Result<Map<String, Object>> claimChef(@PathVariable Long id) {
        return familyService.claimChef(id);
    }

    /** 退出主厨 */
    @PostMapping("/{id}/resign-chef")
    public Result<Void> resignChef(@PathVariable Long id) {
        return familyService.resignChef(id);
    }

    /** 踢人 */
    @PostMapping("/{id}/kick")
    public Result<Void> kick(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object uid = body.get("userId");
        return familyService.kick(id, uid == null ? null : uid.toString());
    }

    /** 主厨状态 */
    @GetMapping("/{id}/chef-status")
    public Result<Map<String, Object>> chefStatus(@PathVariable Long id) {
        return familyService.chefStatus(id);
    }
}