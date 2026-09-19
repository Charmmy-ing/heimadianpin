package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    //逻辑过期时间
    private LocalDateTime expireTime;
    //带有万能数据的对象
    private Object data;
}
