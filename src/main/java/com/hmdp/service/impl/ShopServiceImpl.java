package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.vo.RedisData;
import org.apache.tomcat.jni.Local;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lzc
 * @since 2022-9-10
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return  Result.ok("店铺不存在！");
        }
        return Result.ok(shop);
    }



    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1、更新数据库
        updateById(shop);
        // 2、删除缓存
        stringRedisTemplate.delete("cache:shop:" + id);
        return Result.ok();
    }


    /**
     *  缓存穿透
     * @param id
     * @return
     */
    public Result queryWithPassThrough(Long id){
        String key = "cache:shop:" + id;
        // 1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3、存在,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 判断命中的是否是空值
        if(shopJson != null){
            // 返回一个错误信息
            return Result.fail("店铺信息不存在！");
        }
        // 4、不存在,根据id查询数据库
        Shop shop = getById(id);
        // 5、不存在,返回错误
        if (shop == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在！");
        }
        // 6、存在,写入redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7、返回
        return Result.ok(shop);
    }


    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key = "cache:shop:" + id;
        // 1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3、存在,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值
        if(shopJson != null){
            // 返回一个错误信息
            return null;
        }
        // 4、实现缓存重建
        // 4.1、获取互斥锁
        String lockKey =  RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2、判断是否获取成功
            if(!isLock){
                // 4.3、休眠,则休眠重试并重试
                Thread.sleep(50);
                queryWithMutex(id);
            }
            // 4.4、成功,根据id查询数据库
            shop = getById(id);
            // 模拟缓存重建的延时
            Thread.sleep(200);
            // 5、不存在,返回错误
            if (shop == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6、存在,写入redis中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7、释放互斥锁
            unLock(lockKey);
        }

        // 8、返回
        return shop;

    }


    /**
     *  尝试获取互斥锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
            Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
            return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    /**
     *  后台管理系统
     *  将热点数据提前导入redis中
     */
    public void saveShop2Redis(Long id,Long expireSeconds){
        // 1、查询店铺数据
        Shop shop = getById(id);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 2、封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3、写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     *  逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String key = "cache:shop:" + id;
        // 1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3、不存在,直接返回
              return null;
        }
        // 4、命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5、判断是否过期
        // 过期时间是否在当前时间之后
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1、未过期,直接返回店铺信息
            return shop;
        }
        // 5.2、已过期，缓存重建
        // 6、缓存重建
        // 6.1、获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2、判断是否获取锁成功
        if(isLock){
            // 6.3、成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 重建缓存
                    saveShop2Redis(id,20L);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //释放锁
                    unLock(lockKey);
                }

            });
        }

        // 6.4、返回过期的商铺信息。
        return shop;

    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);




}
