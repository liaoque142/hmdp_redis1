package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)){
//            //3. 存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//        //判断命中的是否是空值
//        if (shopJson != null){
//            // 返回错误信息
//            return Result.fail("店铺不存在redis!");
//        }
//        //4. 不存在，根据id查询数据库
//        Shop shop = getById(id);
//
//        //5. 不存在，返回错误
//        if (shop == null){
//            //将空值写入redis
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//            //返回错误信息
//                return Result.fail("店铺不存在!");
//        }
//
//        //6. 存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
//                CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // TODO 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,id2 -> getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        if (shop == null) {
//            return Result.fail("店铺不存在");
//        }
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);

        //改用逻辑过期来解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在x");
        }
        //7. 返回
        return Result.ok(shop);
    }


    // 创建一个固定 10 个线程的线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期方法
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2. 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3. 不存在，直接返回
            return null;
        }
        // 4.命中，需要根据把JSON反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);

        //简化
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期

        if (expireTime.isAfter(LocalDateTime.now())) {
            //  // 5.1未过期，直接返回店铺信息
            return shop;

        }
        // 5.2已经过期，需要缓存重建


        // 6缓存重建

        // 6.1获取互斥锁
        String lockkey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockkey);

        // 6.2判断是否获取锁成功
        if (isLock) {
            // 6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {

                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockkey);
                }


            });

        }
        // 6.4失败，返回过期商铺信息

        //7. 返回
        return shop;

    }

    ;

    public Shop queryWithMutext(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 命中 → 直接返回（在 try 外面）
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 3. 命中空值 → 直接返回（在 try 外面）
        if (shopJson != null) {
            return null;
        }

        // 4. 抢锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 5. 没抢到 → 重试（在 try 外面）
        if (!isLock) {
            sleep(50);
            return queryWithMutext(id);
        }

        // 6. 抢到了 → 只有这条路径会解锁
        try {
            Shop shop = getById(id);
            sleep(200);

            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                    CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } finally {
            unlock(lockKey);
        }
    }

    // 统一的 sleep
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }


    //重写业务逻辑
//    public Shop queryWithPassThrough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中的是否是空值
//        if (shopJson != null) {
//            // 返回错误信息
//            return null;
//        }
//        //4. 不存在，根据id查询数据库
//        Shop shop = getById(id);
//
//        //5. 不存在，返回错误
//        if (shop == null) {
//            //将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//            //返回错误信息
//            return null;
//        }
//
//        //6. 存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
//                CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        //7. 返回
//        return shop;
//
//    }

    ;


    // 上锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    ;

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1 跟新数据库
        updateById(shop);
        //2 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }


}
