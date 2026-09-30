package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_cart")
public class Cart {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long familyId;
    private Long dishId;
    private String dishName;
    private String dishEmoji;
    private String customIngs;
    private Integer spiceLevel = 0;
    private String remark;
    private BigDecimal price;
    private Integer quantity = 1;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}