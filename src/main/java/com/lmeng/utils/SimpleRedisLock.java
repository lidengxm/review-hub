package com.lmeng.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁的加锁和释放锁
 */

public class SimpleRedisLock implements ILock{
    //业务的名字，后面也是锁的名字，希望不同业务有不同的锁
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //线程id用锁的标示和用户id拼接
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent
                (KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //直接返回success会有自动装箱的操作，有自动装箱就会有空指针危险
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //基于Lua脚本实现分布式锁
        //1.准备lua脚本，在resources目录下新建脚本
        //2.加载lua脚本（静态代码块加载，当类加载时就把lua脚本加了）
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
                );
    }


//    @Override
//    public void unlock() {
//        //获取线程标示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁里面的id
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断线程id和锁里面的id是否相同
//        if(threadId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
