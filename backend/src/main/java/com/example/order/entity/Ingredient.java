package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_ingredient")
public class Ingredient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private Long categoryId;
    /** 克/个/根/把/块/勺/颗 */
    private String unit;
    /** ¥/单位 */
    private BigDecimal price = BigDecimal.ZERO;
    private String emoji;
    private Integer status = 1;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
