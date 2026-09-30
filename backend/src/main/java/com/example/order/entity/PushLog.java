package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_push_log")
public class PushLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String openid;
    private Long familyId;
    private Long menuItemId;
    /** NEW_ORDER / STATUS_CHANGED */
    private String type;
    private Integer success = 0;
    private String errorMsg;
    /** 实际发送的模板数据 JSON，排查时可对照微信后台的推送记录 */
    private String payload;
    private LocalDateTime createdAt;
}
