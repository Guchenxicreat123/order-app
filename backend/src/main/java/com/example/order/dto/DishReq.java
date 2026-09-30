package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 创建/更新菜谱请求 */
@Data
public class DishReq {
    @NotBlank(message = "菜名不能为空")
    private String name;
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
    private String imageEmoji;
    private String description;
    /** 0=免辣 1=微辣 2=重辣 */
    private Integer spiceLevel = 0;
    /** 配菜列表（管理端编辑时传） */
    private List<DishIngredientReq> ingredients;
}
