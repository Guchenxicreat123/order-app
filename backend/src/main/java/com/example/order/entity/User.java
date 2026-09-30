package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.INPUT)
    private String openId;
    private String nickname;
    private String avatarUrl;
    private String phone;
    /** 0=普通成员 1=主厨 */
    private Integer isChef = 0;
    /** 推送订阅授权状态 */
    private Integer allowPush = 0;
    /** 主厨登录 PIN */
    private String chefPin;
    /** 当前操作的家庭 ID */
    private Long activeFamilyId;
    /**
     * JWT 撤销版本号：签发时写入 token 的 tv claim。
     * 请求时与库中值比对，不一致即视为已撤销；递增 = 踢掉该用户全部设备。
     */
    private Integer tokenVersion = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}