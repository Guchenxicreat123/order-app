package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_dish")
public class Dish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private Long categoryId;
    private String imageEmoji;
    /** Markdown 格式做法/步骤/备注 */
    private String description;
    /** 0=免辣 1=微辣 2=重辣 */
    private Integer spiceLevel = 0;
    /** 冗余价格 = 服务层算 */
    private BigDecimal price = BigDecimal.ZERO;
    private Integer status = 1;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
