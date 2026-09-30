package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公共菜配方行（"番茄炒蛋需要 2 个番茄 + 3 个鸡蛋"中的每一行）
 * 关联：
 *   publicDishId → t_public_dish.id    (FK fk_pdi_dish)
 *   ingId        → t_public_ingredient.id (FK fk_pdi_ing)
 */
@Data
@TableName("t_public_dish_ingredient")
public class PublicDishIngredient {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long publicDishId;
    private Long ingId;
    private BigDecimal amount;
    private String unit;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}