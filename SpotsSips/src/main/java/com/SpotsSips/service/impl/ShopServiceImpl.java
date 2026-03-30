package com.SpotsSips.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.Shop;
import com.SpotsSips.mapper.ShopMapper;
import com.SpotsSips.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.SpotsSips.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.SpotsSips.utils.RedisConstants.*;
import static com.SpotsSips.utils.SystemConstants.DEFAULT_PAGE_SIZE;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR =
            Executors.newFixedThreadPool(10);

    /**
     * 处理缓存穿透
     * @param id
     * @return
     */
    @Override
    public Result cachedGetById(Long id) {
        // 从redis查询
        String key = CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);

        // 检查是否存在
        if (StrUtil.isNotBlank(shopStr)) {
            // 查到则返回
            return Result.ok(JSONUtil.toBean(shopStr, Shop.class));
        }

        // 检查是否为null
        if (shopStr != null) {
            // 否，说明应该为先前查询存入的空串，跳过数据库访问
            return Result.fail("商户不存在");
        }

        // 从数据库查询
        Shop shop = getById(id);

        // 检查是否存在
        if (shop == null) {
            // 失败则将空值写入redis并返回fail
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商户不存在");
        }

        // 存储到redis中
        stringRedisTemplate.opsForValue().set(
                key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 返回查询到的结果
        return Result.ok(shop);
    }

//    /**
//     * 互斥锁处理缓存击穿
//     * @param id
//     * @return
//     */
//    @Override
//    public Result cachedGetById(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean locked = false;
//
//        while (true) {
//            // 从redis查询
//            String shopStr = stringRedisTemplate.opsForValue().get(key);
//
//            // 检查是否存在
//            if (StrUtil.isNotBlank(shopStr)) {
//                // 查到则返回
//                return Result.ok(JSONUtil.toBean(shopStr, Shop.class));
//            }
//
//            // 尝试上锁
//            locked = tryLock(lockKey);
//
//            // 检查是否上锁成功
//            if (locked) {
//                Shop shop;
//                try {
//                    // 从redis查询
//                    shopStr = stringRedisTemplate.opsForValue().get(key);
//
//                    // 检查是否存在
//                    if (StrUtil.isNotBlank(shopStr)) {
//                        // 查到则释放锁并返回
//                        unlock(lockKey);
//                        return Result.ok(JSONUtil.toBean(shopStr, Shop.class));
//                    }
//
//                    // 上锁成功，从数据库查询
//                    shop = getById(id);
//
//                    // 模拟重建延迟
//                    // Thread.sleep(200);
//
//                    // 检查是否存在
//                    if (shop == null) {
//                        // 失败则返回fail
//                        return Result.fail("商户不存在");
//                    }
//
//                    // 存储到redis中
//                    stringRedisTemplate.opsForValue().set(
//                            key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                } catch (RuntimeException e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//
//
//                // 返回查询到的结果
//                return Result.ok(shop);
//            }
//
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }

//    /**
//     * 逻辑过期处理缓存击穿
//     * @param id
//     * @return
//     */
//    @Override
//    public Result cachedGetById(Long id) {
//        // 查询缓存
//        String wrappedShopStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 检查是否存在
//        if (wrappedShopStr == null) {
//            // 不存在则返回fail
//            return Result.fail("缓存中不存在给定shop");
//        }
//
//        // 反序列化
//        RedisData wrappedShop = JSONUtil.toBean(wrappedShopStr, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) wrappedShop.getData(), Shop.class);
//
//        // 检查是否过期
//        if (LocalDateTime.now().isBefore(wrappedShop.getExpireTime())) {
//            // 未过期则返回ok
//            return Result.ok(shop);
//        }
//
//        // 尝试上锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean locked = tryLock(lockKey);
//
//        // 检查是否获取锁
//        if (locked) {
//            // double check
//            wrappedShop = JSONUtil.toBean(wrappedShopStr, RedisData.class);
//            shop = JSONUtil.toBean((JSONObject) wrappedShop.getData(), Shop.class);
//            if (LocalDateTime.now().isAfter(wrappedShop.getExpireTime())) {
//                // 如果过期，异步重建缓存，并在异步线程中释放锁
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    try {
//                        queryAndCache(id);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException();
//                    } finally {
//                        unlock(lockKey);
//                    }
//                });
//            } else {
//                unlock(lockKey);
//            }
//        }
//
//        // 返回ok
//        return Result.ok(shop);
//    }

    public void queryAndCache(Long id) throws InterruptedException {
        // 模拟查询延迟
        // Thread.sleep(20);

        Shop shop = getById(id);
        LocalDateTime now = LocalDateTime.now();
        RedisData wrappedShop = new RedisData(now.plusSeconds(20), shop);
        String wrappedShopStr = JSONUtil.toJsonStr(wrappedShop);
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_KEY + id, wrappedShopStr);
    }

    @Override
    @Transactional
    public Result cachedUpdateById(Shop shop) {
        // 检查id是否为空
        if (shop == null) {
            // 空，返回更新失败
            return Result.fail("店铺id不能为空");
        }

        // 更新数据
        updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        // 返回成功
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 检查是否根据位置排序
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 计算分页参数
        int end = current * DEFAULT_PAGE_SIZE;
        int from = end - DEFAULT_PAGE_SIZE;

        // 从redis中查询商铺，按距离排序并分页
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate
                .opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands
                                .GeoSearchCommandArgs
                                .newGeoSearchArgs()
                                .includeDistance()
                                .limit(end)
                );

        // 解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }

        // 截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 返回
        return Result.ok(shops);
    }

    /**
     * 获取锁，成功则返回true
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(success);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
