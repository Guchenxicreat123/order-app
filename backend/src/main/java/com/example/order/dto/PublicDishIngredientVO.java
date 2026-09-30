package com.example.order.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 公共菜详情里的"配方条目"（一行的展示数据）
 * 把 t_public_dish_ingredient 和 t_public_ingredient 合并后的视图
 */
@Data
public class PublicDishIngredientVO {
    private Long id;          // 配方行 id
    private Long ingId;       // 配菜 id
    private String ingName;   // 配菜名
    private String ingEmoji;  // 配菜 emoji
    private Long ingCategoryId;  // 配菜分类 id
    private String ingCategoryName;  // 配菜分类名（前端可用来上色/分块）
    private BigDecimal amount;
    private String unit;      // 用量单位
    private BigDecimal unitPrice;  // 配菜单价（仅供参考，不用于自动算总价）
}