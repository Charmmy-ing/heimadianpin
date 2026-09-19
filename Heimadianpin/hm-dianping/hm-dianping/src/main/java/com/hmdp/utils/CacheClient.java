package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private  StringRedisTemplate stringRedisTemplate;

    //正常设置缓存
    public void set(String key, Object value, Long Time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key,   JSONUtil.toJsonStr(value), Time, timeUnit);
    }
    //缓存击穿-逻辑过期
    public void setWithLogicalExpire(String key, Object value, Long Time, TimeUnit timeUnit) {
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(Time));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T,ID> T queryWhitPassThrough(String keyPrefix, ID id, Class<T> type, Function<ID,T> function, Long Time, TimeUnit timeUnit) {
        //1.根据id查询redis商铺信息缓存
        String Json = stringRedisTemplate.opsForValue().get(keyPrefix+id);
        //2.如果缓存中存在，直接返回缓存中的数据
        if(StringUtils.isNotBlank(Json)){
            // 转换为T对象
            T t = JSONUtil.toBean(Json, type);
            return t;
        }
        //再次从缓存中查询到空值时，返回失败
        if (Json != null){
            return null;
        }
        //3.如果缓存中没有，查询数据库
        T t = function.apply(id);

        //4.如果数据库中也没有
        if(t == null){
            //将空值写入redis缓存，来防止缓存穿透问题
            stringRedisTemplate.opsForValue().set(keyPrefix+id, "", Time, timeUnit);
            return null;
        }
        //5.存在，将数据库中的数据写入redis缓存
        this.set(keyPrefix+id, t, Time, timeUnit);
        //6.返回数据库中的数据
        return t;
    }


    //------------------------------------------------
    //建立一个线程池
    private static final ExecutorService CACHE_REBUILD_POOL  = Executors.newFixedThreadPool(10);

    //用逻辑过期时间解决缓存击穿
    public <R,ID> R queryWhitLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> function, Long expireTimeSeconds) {
        //1.根据id查询redis商铺信息缓存
        String Json = stringRedisTemplate.opsForValue().get(keyPrefix+id);
        //2.如果缓存未命中，直接返回空
        if(StringUtils.isBlank(Json)){

            return null;
        }
        //3.如果缓存命中，把拿到的缓存里的json反序列化为可操作对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        //拿到商铺信息
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //4.判断逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.1没过期，返回信息
        if(expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //4.2过期，缓存重建
        //5.1尝试获取互斥锁
        String lockKey = keyPrefix+id;
        Boolean isLock = tryLock(lockKey);
        //5.2判断是否获取锁
        if(isLock){
            //5.2.2获取到，开启独立线程查询数据库，再将数据写入redis缓存。
            CACHE_REBUILD_POOL.submit(() -> {
                try {
                   R r1 = function.apply(id);
                   //将数据库中的数据写入redis缓存
                   this.saveHotShopRedis(keyPrefix,id, function, expireTimeSeconds);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //5.2.1不管有没有获取到都直接返回旧数据
        return r;
    }
    //模拟缓存预热,给热点key设置逻辑过期
    public <R,ID> void saveHotShopRedis ( String keyPrefix, ID id, Function<ID,R> function, Long expireTimeSeconds){
        //1.根据id查询数据库
        R r = function.apply(id);
        //添加逻辑过期时间
        RedisData  redisData = new RedisData();
        redisData.setData(r);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTimeSeconds));
        //2.将RedisData写入redis缓存
        stringRedisTemplate.opsForValue().set(keyPrefix+id,JSONUtil.toJsonStr(redisData));
    }
    //尝试获取锁
    public boolean tryLock(String key){
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS));
    }
    //解锁
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
