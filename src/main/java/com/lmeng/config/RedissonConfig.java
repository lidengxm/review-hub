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
    private String port;
    private String host;
    //线上环境所需
    private String password;

    @Bean
    public RedissonClient redissonClient() {


        //1.创建配置
        Config config = new Config();
        String redisAddress = String.format("redis://%s:%s",host,port);
        //设置参数（生产环境和测试环境参数不同，注意修改）
        config.useSingleServer()
                .setAddress(redisAddress)
                .setDatabase(0)
                .setPassword(password);
        //2.创建实例
        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;

    }
}
