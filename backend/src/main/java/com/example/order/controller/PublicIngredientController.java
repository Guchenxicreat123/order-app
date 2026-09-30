package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.entity.PublicIngredient;
import com.example.order.service.PublicIngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/ingredients")
public class PublicIngredientController {

    @Autowired private PublicIngredientService publicIngredientService;

    // ==================== 公开接口（无需登录）====================

    /** 公共配菜列表（上架状态） */
    @GetMapping
    public Result<List<PublicIngredient>> list() {
        return publicIngredientService.listPublic();
    }

    /** 公共配菜分类（family_id=0 参考分类） */
    @GetMapping("/categories")
    public Result<List<com.example.order.entity.IngredientCategory>> listCategories() {
        return publicIngredientService.listCategories();
    }

    // ==================== 管理员接口（需主厨权限，见拦截器配置）====================

    /** 管理员查看全部（含下架） */
    @GetMapping("/admin/all")
    public Result<List<PublicIngredient>> listAll() {
        return publicIngredientService.listAll();
    }

    /** 新增公共配菜 */
    @PostMapping
    public Result<PublicIngredient> create(@RequestBody PublicIngredient ing) {
        return publicIngredientService.create(ing);
    }

    /** 编辑公共配菜 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PublicIngredient upd) {
        return publicIngredientService.update(id, upd);
    }

    /** 删除公共配菜 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return publicIngredientService.delete(id);
    }

    /** 批量加入家庭配菜（body: { familyId, publicIngredientIds: [] }） */
    @PostMapping("/to-family")
    public Result<Map<String, Object>> addToFamily(@RequestBody Map<String, Object> body) {
        Long familyId = body.get("familyId") == null ? null
                : Long.valueOf(body.get("familyId").toString());
        @SuppressWarnings("unchecked")
        List<Long> ids = body.get("publicIngredientIds") == null ? null
                : ((List<Object>) body.get("publicIngredientIds")).stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .collect(java.util.stream.Collectors.toList());
        return publicIngredientService.addToFamily(familyId, ids);
    }
}
