package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 下单人用户ID */
    private String userId;
    /** 下单人昵称（冗余） */
    private String userNickname;
    /** 订单总价（所有菜品价格之和） */
    private BigDecimal totalAmount = BigDecimal.ZERO;
    /** 菜品数量 */
    private Integer itemCount = 0;
    /** 0=待确认 1=已确认 -1=已撤销 */
    private Integer status = 0;
    private String remark;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
