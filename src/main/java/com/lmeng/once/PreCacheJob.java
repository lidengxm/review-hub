package com.lmeng.once;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lmeng.constant.SystemConstants;
import com.lmeng.global.UserHolder;
import com.lmeng.model.Blog;
import com.lmeng.model.User;
import com.lmeng.service.IShopService;
import com.lmeng.service.IUserService;
import com.lmeng.service.impl.BlogServiceImpl;
import com.lmeng.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lmeng.constant.RedisConstants.*;

/**
 * 缓存预热 首页热点博客
 */
@Slf4j
@Component
public class PreCacheJob {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private BlogServiceImpl blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //每天12点定时执行
    @Scheduled(cron = "0 0 12 * * *")
    public void doPreCacheJob() {
        // 获取Redisson分布式锁
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            //尝试获取分布式锁成功
            if (lock.tryLock(0,3000L,TimeUnit.MILLISECONDS)) {
                //根据点赞数量分页查询热点博客
                Page<Blog> page = blogService.query()
                        .orderByDesc("liked")
                        .page(new Page<>(1, SystemConstants.MAX_PAGE_SIZE));
                //获取当前页数据
                List<Blog> records = page.getRecords();
                String key = CACHE_HOT_BLOG_KEY;
                //写入热点博客缓存
                try {
                    //以JSON格式存入缓存
                    stringRedisTemplate.opsForValue().set(key,
                            JSONUtil.toJsonStr(records),CACHE_HOT_BLOG_TTL, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.error("redis set key error",e);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            //执行完任务一定要释放锁（先检查是否是当前线程加的锁）
            System.out.println("unLock: "+Thread.currentThread().getId());
            //判断是否是自己的锁，保证不会释放别人的锁
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
