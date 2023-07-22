package com.lmeng.once;

import com.lmeng.service.IShopService;
import com.lmeng.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 缓存预热热点数据
 */
@Slf4j
@Component
public class PreCacheJob {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IShopService shopService;

    @Resource
    private RedisTemplate redisTemplate;


}
