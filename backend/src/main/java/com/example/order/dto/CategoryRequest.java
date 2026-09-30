package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CategoryRequest {
    @NotBlank(message = "分类名不能为空")
    private String name;

    @NotNull(message = "排序不能为空")
    private Integer sort;

    private Integer status = 1;
}
