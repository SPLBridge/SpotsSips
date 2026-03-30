package com.SpotsSips.utils;

public interface RedisLock {
    /**
     * 获取锁
     * @param timeoutSec 上锁时间（秒）
     * @return 是否获取成功
     */
    boolean trylock(String keyName, long timeoutSec);

    /**
     * 释放锁
     */
    void unlock(String keyName);
}
