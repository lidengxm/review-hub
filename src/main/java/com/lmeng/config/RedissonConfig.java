package com.lmeng.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @version 1.0
 * @learner Lmeng
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://124.220.224.98:6379")
                .setPassword("123321");

        //创建RedissonClient对象
        return Redisson.create(config);

    }
}
