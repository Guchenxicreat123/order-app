package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.entity.PublicDish;
import com.example.order.service.PublicDishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/dishes")
public class PublicDishController {

    @Autowired private PublicDishService publicDishService;

    // ==================== 公开接口（无需登录）====================

    /** 公开菜品列表（上架状态） */
    @GetMapping
    public Result<List<PublicDish>> list() {
        return publicDishService.listPublic();
    }

    /**
     * 公开菜品列表（含每道菜的配方）
     * 用于"小程序启动一次性拉全部数据到内存"，避免 N+1 请求
     */
    @GetMapping("/with-ingredients")
    public Result<List<Map<String, Object>>> listWithIngredients() {
        return publicDishService.listPublicWithIngredients();
    }

    /** 公开菜品详情（含配方） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return publicDishService.getById(id);
    }

    /** 批量取多道菜的详情（含配方）。GET ?ids=1,2,3 */
    @GetMapping("/batch-detail")
    public Result<Map<Long, Map<String, Object>>> batchDetail(@RequestParam("ids") String idsParam) {
        List<Long> ids = new ArrayList<>();
        if (idsParam != null && !idsParam.isEmpty()) {
            for (String s : idsParam.split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (Exception ignore) {}
            }
        }
        return publicDishService.getManyWithIngredients(ids);
    }

    // ==================== 管理员接口（需主厨权限，见拦截器配置）====================

    /** 管理员查看全部（含下架） */
    @GetMapping("/admin/all")
    public Result<List<PublicDish>> listAll() {
        return publicDishService.listAll();
    }

    /** 新增公共菜品 */
    @PostMapping
    public Result<PublicDish> create(@RequestBody PublicDish dish) {
        return publicDishService.create(dish);
    }

    /** 编辑公共菜品 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PublicDish upd) {
        return publicDishService.update(id, upd);
    }

    /** 删除公共菜品 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return publicDishService.delete(id);
    }
}
