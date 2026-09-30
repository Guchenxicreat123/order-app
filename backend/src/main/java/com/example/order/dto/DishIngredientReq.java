package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 菜谱-配菜关联编辑请求 */
@Data
public class DishIngredientReq {
    @NotNull(message = "配菜ID不能为空")
    private Long ingId;
    @NotNull(message = "用量不能为空")
    private BigDecimal amount;
    private String unit;
}
