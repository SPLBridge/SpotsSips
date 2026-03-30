package com.SpotsSips.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.VoucherOrder;
import com.SpotsSips.mapper.VoucherOrderMapper;
import com.SpotsSips.service.ISeckillVoucherService;
import com.SpotsSips.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.SpotsSips.utils.RedisIDWorker;
import com.SpotsSips.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.SpotsSips.utils.RedisConstants.*;
import static com.SpotsSips.utils.SystemConstants.LUA_PATH;
import static com.SpotsSips.utils.SystemConstants.LUA_SUFFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIDWorker idWorker;

//    @Autowired
//    private RedisLock redisLock;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注入自己，用于保证数据库操作的原子性
     */
    @Autowired
    @Lazy
    private IVoucherOrderService self;

    private final BlockingQueue<VoucherOrder> bq =
            new ArrayBlockingQueue<>(1 << 20);


    /**
     * 在Bean初始化后开启另一个线程专门用于访问数据库，没有订单传入时由阻塞队列阻塞
     */
    @PostConstruct
    private void init() {
        // 初始化数据库访问线程
        ExecutorService ex = Executors.newSingleThreadExecutor();

        // 新建线程用于不断接收消息并访问数据库
        ex.submit((Runnable) () -> {
            while (true) {
                try {
                    // 尝试获取消息
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().block(Duration.ofSeconds(2L)).count(1),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );

                    // 检查是否有消息
                    if (records == null || records.isEmpty()) {
                        continue;
                    }

                    // 访问数据库并确认信息
                    self.updateDBAndACK(records.get(0));
                } catch (Exception e) {
                    // 记录日志
                    log.info("订单异常", e);

                    // 处理剩余未读消息
                    handlePandingList();
                }
            }
        });
    }

    private void handlePandingList() {
        while (true) {
            try {
                // 尝试获取消息
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                );

                // 检查是否为空
                if (records == null || records.isEmpty()) {
                    break;
                }

                // 访问数据库并确认信息
                self.updateDBAndACK(records.get(0));
            } catch (Exception e) {
                // 记录日志
                log.info("再次异常", e);

                // 防止频繁错误
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    /**
     * 扣减库存并将订单信息保存到数据库中，理论上不会发生意外
     * @param record redis已保存的订单记录
     */
    @Transactional
    public void updateDBAndACK(MapRecord<String, Object, Object> record) {
        // 获取订单
        VoucherOrder order = BeanUtil.fillBeanWithMap(
                record.getValue(), new VoucherOrder(), false);

        // 扣减库存
        seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .update();

        // 将订单信息保存到数据库中
        save(order);

        // 确认消息
        stringRedisTemplate.opsForStream().acknowledge("g1", record);
    }

    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    private static final String QUEUE_NAME = "stream.orders";

    static {
        // 输入lua脚本
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource(LUA_PATH + "seckill" + LUA_SUFFIX));
        REDIS_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckill(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();

        // 创建订单id
        long orderID = idWorker.nextID(SECKILL_ORDER_KEY);

        // 1.执行lua脚本，发送消息
        long result = stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Arrays.asList(voucherId.toString(), HAVING_ORDERED_KEY + voucherId),
                userId.toString(), String.valueOf(orderID)
        );

        // 检查是否执行成功
        if (result == 1) {
            // 库存为0
            return Result.fail("抢完啦~");
        }

        if (result == 2) {
            // 重复下单
            return Result.fail("已经下过单啦，给别的小伙伴留点机会吧~");
        }

        if (result == 3) {
            log.info("用户访问了缓存中不存在的优惠券id: " + voucherId);
            return Result.fail("服务器异常");
        }
//
//        // 创建订单对象
//        VoucherOrder order = new VoucherOrder();
//        order.setVoucherId(voucherId);
//        order.setUserId(userId);
//        order.setId(orderID);
//
//        // 将订单放入阻塞队列中
//        bq.add(order);

        return Result.ok(orderID);
    }

//    @Override
//    public Result seckill(Long voucherId) {
//        // 获取秒杀券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        // 获取当前时间
//        LocalDateTime now = LocalDateTime.now();
//
//        // 判断是否在有效时间内
//        if (now.isBefore(voucher.getBeginTime()) || now.isAfter(voucher.getEndTime())) {
//            return Result.fail("现在不可以秒杀哦~");
//        }
//
//        // 判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("抢完啦~");
//        }
//
//        // 获取用户id
//        Long userID = UserHolder.getUser().getId();
//
//        // 尝试获取锁
//        String lockKeyName = SECKILL_ORDER_KEY + userID;
////        boolean gotten = redisLock.trylock(lockKeyName, SECKILL_ORDER_LOCK_TTL);
//        RLock lock = redissonClient.getLock(lockKeyName);
//        boolean gotten = lock.tryLock();
//
//        // 检查是否获取锁
//        if (!gotten) {
//            return Result.fail("你已经下过单啦，留点给别的小伙伴吧~");
//        }
//
//        try {
//            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
////            redisLock.unlock(lockKeyName);
//            lock.unlock();
//        }
//    }
//
//    @Transactional
//    @NonNull
//    public Result createVoucherOrder(Long voucherId) {
//        // 查询该用户是否下过单
//        // 1. 获取用户id
//        Long userID = UserHolder.getUser().getId();
//
//        // 2. 获取该id对应订单数
//        Integer orderCount = query()
//                .eq("user_id", userID)
//                .count();
//
//        // 3. 检查是否已有订单
//        if (orderCount > 0) {
//            // 已有，返回fail
//            return Result.fail("你已经下过单啦，给别人留点机会吧~");
//        }
//
//        // 扣减库存
//        boolean success = seckillVoucherService
//                .update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0)
//                .update();
//
//        // 检查是否更新成功
//        if (!success) {
//            return Result.fail("抢完啦~");
//        }
//
//        // 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = idWorker.nextID(SECKILL_STOCK_KEY);
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userID);
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }
}
