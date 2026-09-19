package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final Long BEGIN_TIME = 1640995200L;
    private final Long TIMESTAMP_BITS = 32L;
    //ID生成器，基于Redis的原子性操作
    public Long nextId(String keyPrefix) {
        //id生成策略-雪花算法
        //1.获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        LocalDate expireTime = LocalDate.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIME;
        //2.序列号
        long id = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + expireTime);
        //3.拼接时间戳和ID数量，生成最终的ID
        return timestamp << TIMESTAMP_BITS | id;
    }

}