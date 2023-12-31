package com.lmeng.once;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @version 1.0
 * @learner Lmeng
 */

@Component
public class RedisWorker {

    private StringRedisTemplate stringRedisTemplate;

    public RedisWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1672531200;

    /**
     * 序列号位数
     */
    private static final long COUNT_BITS = 32;

    /**
     * 生成订单唯一id
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix) {
        //1.生成时间戳（时间戳=当前时间-初始时间）
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1获取当前日期，精确到天，方便统计，避免重复的key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2进行序列号自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接成ID后返回，或运算连接诶
        //数字拼接，两个long类型的值拼接
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime currentTime = LocalDateTime.of(2023,1,1,0,0,0);
        //将当前时间转为对应的秒数
        long second = currentTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);

    }

}
