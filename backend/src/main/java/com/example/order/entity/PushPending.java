package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_push_pending")
public class PushPending {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String openid;
    private String templateId;
    /** NEW_ORDER / STATUS_CHANGED —— 重试时要靠它决定推给谁、填充哪些字段 */
    private String pushType;
    /** JSON 模板数据 */
    private String payload;
    private Long menuItemId;
    private Long familyId;
    private Integer attempts = 0;
    private LocalDateTime nextRetryAt;
    /** 0=pending 1=done 2=abandoned */
    private Integer status = 0;
    private String lastError;
    private LocalDateTime createdAt;
}
