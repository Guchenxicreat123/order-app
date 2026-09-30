package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名不能为空")
    private String name;

    private String description;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private String imageUrl;

    private Integer status = 1;

    private Integer stock = 999;
}
