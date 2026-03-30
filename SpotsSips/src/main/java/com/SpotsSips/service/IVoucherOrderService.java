package com.SpotsSips.service;

import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.data.redis.connection.stream.MapRecord;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckill(Long voucherId);

    void updateDBAndACK(MapRecord<String, Object, Object> record);
}
