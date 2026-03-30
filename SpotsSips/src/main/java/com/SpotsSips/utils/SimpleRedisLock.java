package com.SpotsSips.utils;

import cn.hutool.core.util.BooleanUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Component
public class SimpleRedisLock implements RedisLock{
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String PREFIX = "lock:";
    private static final String uuid = UUID.randomUUID().toString();

    @Override
    public boolean trylock(String keyName, long timeoutSec) {
        // 获取线程id
        String threadId = uuid + Thread.currentThread().getId();

        // 上锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                PREFIX + keyName, threadId, timeoutSec, TimeUnit.MINUTES);

        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock(String keyName) {
        // 获取线程id
        String threadId = uuid + Thread.currentThread().getId();

        // 获取锁中的id
        String savedId = stringRedisTemplate.opsForValue().get(PREFIX + keyName);

        // 检查是否相同
        if (threadId.equals(savedId)) {
            // 释放锁
            stringRedisTemplate.delete(PREFIX + keyName);
        }
    }
}
