package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY + "list";

        // 1. 先查 Redis
        List<String> cacheList = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 2. 命中 → 反序列化返回
        if (CollUtil.isNotEmpty(cacheList)) {
            List<ShopType> typeList = cacheList.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(typeList);
        }

        // 3. 没命中 → 查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 4. 数据库也空 → 直接返回（空集合不能写 Redis）
        if (CollUtil.isEmpty(typeList)) {
            return Result.ok(typeList);
        }

        // 5. 写回 Redis + 设过期时间
        List<String> jsonList = typeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, jsonList);
        stringRedisTemplate.expire(key, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        // 6. 返回
        return Result.ok(typeList);
    }
}
