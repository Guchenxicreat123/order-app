package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_menu_item_rating")
public class MenuItemRating {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long menuItemId;
    private String userId;
    /** 1-5 星 */
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
