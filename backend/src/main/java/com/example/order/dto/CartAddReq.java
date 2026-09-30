package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 加入购物车请求 */
@Data
public class CartAddReq {
    /** 固定菜 ID，null=自定义菜 */
    private Long dishId;
    /** 自定义菜名称 */
    private String dishName;
    /** 自定义菜配料 JSON */
    private String customIngs;
    /** 辣度 */
    private Integer spiceLevel = 0;
    /** 备注 */
    private String remark;
    /** 加入数量（默认 1，累加到现有数量上） */
    private Integer quantity = 1;
}

/** 批量下单请求 */
@Data
class CartCheckoutReq {
    private List<Long> cartIds;
}