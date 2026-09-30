package com.example.order.dto;

import lombok.Data;

/** 推送授权请求 */
@Data
public class PushAuthReq {
    private boolean allow; // true=授权 false=取消授权
}
