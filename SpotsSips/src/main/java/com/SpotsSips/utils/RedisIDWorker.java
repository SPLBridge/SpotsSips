package com.SpotsSips.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    private static final long BEGIN_TIME_STAMP = 1773156060L;
    private static final int COUNTER_BITS = 32;
    private static final String PREFIX = "icr:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 对每一个业务产生唯一自增的id
     * @param prefix 键的前缀，用于区分不同业务
     * @return
     */
    public long nextID(String prefix) {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 生成时间戳
        long timeStamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIME_STAMP;

        // 生成流水号
        String timePrefix = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd:"));
        long counter = stringRedisTemplate.opsForValue().increment(PREFIX + prefix + timePrefix);

        // 拼接为id
        long id = timeStamp << COUNTER_BITS | counter;

        return id;
    }

}
