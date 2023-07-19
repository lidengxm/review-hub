package com.lmeng.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    /**
     * 过期时间（逻辑过期）
     */
    private LocalDateTime expireTime;

    /**
     * 缓存对象
     */
    private Object data;
}
