package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_menu_item")
public class MenuItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long familyId;
    /** 所属订单ID（一个订单下所有菜品共享同一个 orderId） */
    private Long orderId;
    /** 冗余下单人昵称 */
    private String userNickname = "";
    /** null=自定义菜 */
    private Long dishId;
    private String dishName;
    private String dishEmoji;
    /** 自定义菜配料 JSON: [{ingId,amount,unit,name,price}] */
    private String customIngs;
    /** 0=免辣 1=微辣 2=重辣 */
    private Integer spiceLevel = 0;
    /** 后端实时算，前端不传 */
    private BigDecimal price = BigDecimal.ZERO;
    private String remark;
    /** -1=已撤销 0=已下单 1=已确认 */
    private Integer status = 0;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    /** 乐观锁版本号 */
    private Integer version = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
