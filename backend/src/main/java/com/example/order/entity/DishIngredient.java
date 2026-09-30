package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("t_dish_ingredient")
public class DishIngredient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long dishId;
    private Long ingId;
    /** 用量（数值，避免精度丢失） */
    private BigDecimal amount;
    /** 冗余显示单位 */
    private String unit = "";
}
