package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SimpleRedisLock implements Ilock {
    String name;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final String KEY_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true)+":";
    @Override
    public boolean tryLock(Long timeout, String name) {
        //获取线程名称
        long threadName = Thread.currentThread().getId();
        this.name = name;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, ID_PREFIX + threadName + "", timeout, TimeUnit.SECONDS));

    }
    @Override
    public void unlock() {
        //获取线程名称
        long threadName = Thread.currentThread().getId();
        //判断是否是当前线程获取的锁
        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (lockId == null) {
            return;
        }
        if (lockId.equals(ID_PREFIX + threadName + "")) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
