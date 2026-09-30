package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 创建菜单项请求 */
@Data
public class CreateMenuItemReq {
    /** 固定菜 ID，null=自定义菜 */
    private Long dishId;
    /** 自定义菜的名称（用户指定） */
    private String dishName;
    /** 自定义菜配料 JSON: [{ingId, amount, unit, name}] */
    private String customIngs;
    /** 辣度 0=免辣 1=微辣 2=重辣 */
    private Integer spiceLevel = 0;
    /** 备注 */
    private String remark;
}
