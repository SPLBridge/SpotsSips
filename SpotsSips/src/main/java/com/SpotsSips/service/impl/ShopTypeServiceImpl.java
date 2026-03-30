package com.SpotsSips.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.ShopType;
import com.SpotsSips.mapper.ShopTypeMapper;
import com.SpotsSips.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.SpotsSips.utils.RedisConstants.SHOP_TYPE_KEY;
import static com.SpotsSips.utils.RedisConstants.SHOP_TYPE_TTL;
import static java.util.concurrent.TimeUnit.MINUTES;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result cachedGetTypeList() {
        // 获取redis中缓存的类型列表
        String typeListStr = stringRedisTemplate.opsForValue().get(SHOP_TYPE_KEY);

        // 检查缓存是否存在
        if (StrUtil.isNotBlank(typeListStr)) {
            // 存在则直接返回
            List<ShopType> list = JSONUtil.toList(typeListStr, ShopType.class);
            return Result.ok(list);
        }

        // 从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 检查是否存在
        if (typeList == null) {
            // 不存在则返回fail
            Result.fail("未查询到商户类型");
        }

        // 保存到缓存中
        stringRedisTemplate.opsForValue().set(
                SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList), SHOP_TYPE_TTL, MINUTES);

        // 返回
        return Result.ok(typeList);
    }
}
