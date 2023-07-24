package com.lmeng.redisson;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @learner Lmeng
 * @date 2023/7/22
 */
@SpringBootTest
@Slf4j
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    void setup() {
        lock = redissonClient.getLock("order");
    }

    @Test
    void method1() throws InterruptedException {
        //尝试获得锁
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if(!isLock) {
            log.error("获取锁失败");
            return;
        }
        try {
            log.info("获取锁成功 1");
            log.info("执行业务 1");
        } finally {
            log.info("准备释放锁 1");
            lock.unlock();
        }
    }

    void method2() {
        //尝试获得锁
        boolean isLock = lock.tryLock();
        if(!isLock) {
            log.error("获取锁失败");
            return;
        }
        try {
            log.info("获取锁成功 2");
            log.info("执行业务 2");
        } finally {
            log.info("准备释放锁 2");
            lock.unlock();
        }
    }


}
