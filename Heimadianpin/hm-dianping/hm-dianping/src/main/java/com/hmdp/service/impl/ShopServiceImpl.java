package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
         //Shop shop = cacheClient.queryWhitPassThrough(CACHE_SHOP_KEY,id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //用互斥锁解决缓存击穿
        //Shop shop = queryWhitPassMutex(id);

        //用逻辑过期时间解决缓存击穿
        Shop shop =cacheClient.queryWhitLogicalExpire(CACHE_SHOP_KEY,id, Shop.class, this::getById, CACHE_SHOP_TTL);

        {
            if (shop == null) {
                return Result.fail("商铺不存在");
            }
        }

        //6.返回数据库中的数据
        return Result.ok(shop);
    }
    //建立一个线程池
    private static final ExecutorService CACHE_REBUILD_POOL  = Executors.newFixedThreadPool(10);

    //用逻辑过期时间解决缓存击穿
//    public Shop queryWhitLogicalExpire(Long id) {
//        //1.根据id查询redis商铺信息缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2.如果缓存未命中，直接返回空
//        if(StringUtils.isBlank(shopJson)){
//
//            return null;
//        }
//        //3.如果缓存命中，把拿到的缓存里的json反序列化为可操作对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        //拿到商铺信息
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //4.判断逻辑过期时间
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //4.1没过期，返回信息
//        if(expireTime.isAfter(LocalDateTime.now())) {
//            return shop;
//        }
//        //4.2过期，缓存重建
//        //5.1尝试获取互斥锁
//       String lockKey = CACHE_SHOP_KEY+id;
//       Boolean isLock = tryLock(lockKey);
//       //5.2判断是否获取锁
//        if(isLock){
//            //5.2.2获取到，开启独立线程查询数据库，再将数据写入redis缓存。
//            CACHE_REBUILD_POOL.submit(() -> {
//                try {
//                    saveHotShopRedis(id,20L);
//
//
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        //5.2.1不管有没有获取到都直接返回旧数据
//        return shop;
//    }

//    //模拟缓存预热,给热点key设置逻辑过期
//    public void saveHotShopRedis(Long id,Long expireTimeSeconds){
//        //1.根据id查询数据库
//        Shop shop = getById(id);
//        //添加逻辑过期时间
//        RedisData  redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTimeSeconds));
//        //2.将RedisData写入redis缓存
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }

    //用互斥锁解决缓存击穿
//    public Shop queryWhitPassMutex(Long id) {
//        //1.根据id查询redis商铺信息缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2.如果缓存中存在，直接返回缓存中的数据
//        if(StringUtils.isNotBlank(shopJson)){
//            // 转换为Shop对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //再次从缓存中查询到空值时，返回失败
//        if (shopJson!= null){
//            return null;
//        }
//        //实现缓存重建
//        String lockKey = CACHE_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            //1.获取互斥锁
//            Boolean isLock = tryLock(lockKey);
//            //2.判断是否获取到锁
//            if (!isLock) {
//                //失败，则休眠之后再重试
//                Thread.sleep(50);
//                return queryWhitPassMutex(id);
//            }
//            //成功，则继续执行后续操作
//            //3.查询数据库
//            shop = getById(id);
//            //4.如果数据库中也没有，返回失败
//            if (shop == null) {
//                //将空值写入redis缓存，来防止缓存穿透问题
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                //返回失败
//                return null;
//            }
//            //5.存在，将数据库中的数据写入redis缓存
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        }catch (InterruptedException e){
//            throw new RuntimeException(e);
//        }finally {
//            //6.解锁
//            unlock(lockKey);
//        }
//        //6.返回数据库中的数据
//        return shop;
//    }

    //    // 带穿透的查询商铺信息
//    public Shop queryWhitPassThrough(Long id) {
//         //1.根据id查询redis商铺信息缓存
//         String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//         //2.如果缓存中存在，直接返回缓存中的数据
//         if(StringUtils.isNotBlank(shopJson)){
//             // 转换为Shop对象
//             Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//             return shop;
//         }
//         //再次从缓存中查询到空值时，返回失败
//         if (id == null){
//             return null;
//         }
//         //3.如果缓存中没有，查询数据库
//         Shop shop = getById(id);
//
//         //4.如果数据库中也没有，返回失败
//         if(shop == null){
//             //将空值写入redis缓存，来防止缓存穿透问题
//             stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//             return null;
//         }
//         //5.存在，将数据库中的数据写入redis缓存
//         stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//         //6.返回数据库中的数据
//         return shop;
//     }

//    //尝试获取锁
//    public boolean tryLock(String key){
//        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue()
//                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS));
//    }
//    //解锁
//    public void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

    //更新商铺信息+事务
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if(shopId == null){
            return Result.fail("商铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存中的商铺信息缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        //3.返回成功
        return Result.ok();
    }

}
