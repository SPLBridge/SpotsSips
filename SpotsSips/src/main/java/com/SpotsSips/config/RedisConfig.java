package com.SpotsSips.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private String port;

    @Value("${spring.redis.password}")
    private String password;

    private static final String REDIS_PREFIX = "redis://";
    @Bean
    public RedissonClient redissonClient() {
        // 创建redisson配置对象
        Config config = new Config();

        // 配置redisson
        config.useSingleServer() // 使用单例模式，也可以用useClusterServers()添加集群地址
                .setAddress(REDIS_PREFIX + host + ":" + port) // 设置redis ip
                .setPassword(password); // 设置密码

        return Redisson.create(config);
    }
}
