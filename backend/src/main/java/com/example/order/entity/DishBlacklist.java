package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_dish_blacklist")
public class DishBlacklist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long dishId;
    private LocalDateTime createdAt;
}
