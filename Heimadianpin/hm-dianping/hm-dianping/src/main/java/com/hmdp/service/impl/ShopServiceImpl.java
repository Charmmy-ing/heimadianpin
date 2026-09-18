package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        // Shop shop = queryWhitPassThrough(id);

        //用互斥锁解决缓存击穿
        Shop shop = queryWhitPassMutex(id);
        {
            if (shop == null) {
                return Result.fail("商铺不存在");
            }
        }

        //6.返回数据库中的数据
        return Result.ok(shop);
    }
    public Shop queryWhitPassMutex(Long id) {
        //1.根据id查询redis商铺信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        //2.如果缓存中存在，直接返回缓存中的数据
        if(StringUtils.isNotBlank(shopJson)){
            // 转换为Shop对象
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //再次从缓存中查询到空值时，返回失败
        if (shopJson!= null){
            return null;
        }
        //实现缓存重建
        String lockKey = CACHE_SHOP_KEY + id;
       Shop shop = null;
       try {
           //1.获取互斥锁
           Boolean isLock = tryLock(lockKey);
           //2.判断是否获取到锁
           if (!isLock) {
               //失败，则休眠之后再重试
               Thread.sleep(50);
               return queryWhitPassMutex(id);
           }
           //成功，则继续执行后续操作
           //3.查询数据库
           shop = getById(id);
           //4.如果数据库中也没有，返回失败
           if (shop == null) {
               //将空值写入redis缓存，来防止缓存穿透问题
               stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
               //返回失败
               return null;
           }
           //5.存在，将数据库中的数据写入redis缓存
           stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
       }catch (InterruptedException e){
          throw new RuntimeException(e);
       }finally {
        //6.解锁
        unlock(lockKey);
        }
        //6.返回数据库中的数据
        return shop;
    }

    // 带穿透的查询商铺信息
     public Shop queryWhitPassThrough(Long id) {
         //1.根据id查询redis商铺信息缓存
         String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
         //2.如果缓存中存在，直接返回缓存中的数据
         if(StringUtils.isNotBlank(shopJson)){
             // 转换为Shop对象
             Shop shop = JSONUtil.toBean(shopJson, Shop.class);
             return shop;
         }
         //再次从缓存中查询到空值时，返回失败
         if (id == null){
             return null;
         }
         //3.如果缓存中没有，查询数据库
         Shop shop = getById(id);

         //4.如果数据库中也没有，返回失败
         if(shop == null){
             //将空值写入redis缓存，来防止缓存穿透问题
             stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
             return null;
         }
         //5.存在，将数据库中的数据写入redis缓存
         stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

         //6.返回数据库中的数据
         return shop;
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
