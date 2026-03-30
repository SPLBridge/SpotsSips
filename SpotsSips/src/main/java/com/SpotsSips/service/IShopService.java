package com.SpotsSips.service;

import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result cachedGetById(Long id);

    Result cachedUpdateById(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
