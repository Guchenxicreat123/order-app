package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_family")
public class Family {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String ownerUserId;
    private LocalDateTime createdAt;
}