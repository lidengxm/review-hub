package com.hmdp.utils;

/**
 * @version 1.0
 * @learner Lmeng
 */
public interface ILock {

    /**
     * 尝试获取锁
     *
     *
     */

    boolean tryLock(long timeoutSec) ;

    /**
     * 释放锁
     */
    void unlock();

}
