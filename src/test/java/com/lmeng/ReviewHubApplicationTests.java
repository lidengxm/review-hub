package com.lmeng;

import com.lmeng.model.Shop;
import com.lmeng.once.RedisWorker;
import com.lmeng.service.impl.ShopServiceImpl;
import com.lmeng.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.lmeng.constant.RedisConstants.CACHE_SHOP_KEY;
import static com.lmeng.constant.RedisConstants.SHOP_GEO_KEY;


@SpringBootTest
public class ReviewHubApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisWorker redisWorker;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test
    void testIdWorker( ) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            //每个线程都生成100次ID
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextId("order");
                System.out.println("id = " + id);
            }
            //递减闩锁的计数，如果计数达到零，则释放所有等待的线程。
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //当前线程等待，直到所有线程结束任务
        latch.await();
        long end = System.currentTimeMillis();
        //计算执行时间
        System.out.println("time = " + (end - begin));

    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void loadShopData() {
        // 1.查询店铺信息，店铺信息不多全部查询
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取店铺类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    /**
     * 测试插入100 0000条数据
     */
    @Test
    void testHyperLogLog() {
        //准备数组，装用户数据
        String[] values = new String[1000];
        //数组角标
        int j = 0;
        for(int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999) {
                //发送到redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        //统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count=" + count);
    }

    /**
     * 测试使用Redsisson实现分布式锁
     */
    @Test
    void testRedisson() throws InterruptedException{
        //获取可重入锁
        RLock lock = redissonClient.getLock("shop:");
        //尝试获取锁，参数分别是获取锁的最大之间，锁自动释放，时间单位
        boolean success = lock.tryLock(3,10,TimeUnit.MINUTES);
        if(success) {
            try {
                System.out.println("执行业务");
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 测试获取热点数据
     */
//    @Test
//    void testGetHotShopList() {
//        List<Shop> hotShopList = shopService.getHotShopList();
//        for (Shop shop : hotShopList) {
//            System.out.println(shop.toString());
//        }
//    }

}
