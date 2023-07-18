package com.lmeng.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lmeng.dto.ResultUtils;
import com.lmeng.model.Shop;
import com.lmeng.mapper.ShopMapper;
import com.lmeng.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lmeng.utils.CacheClient;
import com.lmeng.constant.SystemConstants;
import com.lmeng.utils.RedisData;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.lmeng.constant.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * **给查询商铺的缓存添加超时剔除和主动更新的策略
     * @param id
     * @return
     */
    @Deprecated
    public ResultUtils queryByIdCacheUpdate(Long id) {
        //1.从Redis中查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //2.缓存存在就返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return ResultUtils.ok(shop);
        }
        //3.缓存不存在，查询数据库
        Shop shop = getById(id);
        //4.写入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        //5.返回
        return ResultUtils.ok(shop);
    }

    /**
     * 使用缓存查询店铺信息
     * @param id
     * @return
     */
    @Override
    public ResultUtils queryById(Long id) {
        //1.从Redis中查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //2.缓存存在就返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return ResultUtils.ok(shop);
        }
        //判断命中的缓存是否是空值
        if (shopJson != null) {
            return ResultUtils.fail("店铺信息不存在！");
        }

        //3.缓存不存在，查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //将空值写入缓存
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return ResultUtils.fail("商铺不存在！");
        }
        //4.写入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        //5.返回
        return ResultUtils.ok(shop);
    }

    @Deprecated
    public ResultUtils queryById1(Long id) {
        //缓存穿透
//        Shop shop = queryWithThrough(id);
        //解决缓存穿透
        Shop shop = cacheClient.
                queryWithThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
//        Shop shop = cacheClient.
//                queryWithMutex(CACHE_SHOP_KEY,id, Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //逻辑过期解决缓存击穿
        //Shop shop = queryWithExpire(id);
//        Shop shop = cacheClient.
//                queryWithExpire(CACHE_SHOP_KEY,id, Shop.class,this::getById,20L,TimeUnit.MINUTES);

        if(shop == null) {
            return ResultUtils.fail("店铺不存在");
        }
        //7.返回
        return ResultUtils.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//     // 逻辑过期解决缓存穿透
//    public Shop queryWithExpire(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断缓存是否命中？
//        if (StrUtil.isBlank(shopJson)) {
//            //3.存在，直接返回null
//            return null;
//        }
//
//        //4.命中，需要把Json反序列化成对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断缓存是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //6.未过期，直接返回店铺信息
//            return shop;
//        }
//        //7.如果过期，需要缓存重建；缓存重建要先获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //8.判断是否获取互斥锁
//        if(isLock) {
//            //获取锁之后应再次检测redis缓存是否过期，如果存在则无需重建缓存
//            if (StrUtil.isBlank(shopJson)) {
//                //3.存在，直接返回null
//                return null;
//            }
//            //9.成功，开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveSHop2Redis(id,20L);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        //10.返回过期的商铺信息
//        return shop;
//    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutexByXLock(Long id) {
        //1.判断缓存是否存在
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中？
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            //需要返回的类型是Shop类型，所有需要用JsonUtil工具类将JSON格式转成Shop类型
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值（缓存控制解决缓存穿透）
        if ("".equals(shopJson)) {
            return null;
        }

        //4.开始实现缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + key;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断互斥锁是否获取成功
            if (!isLock) {
                //4.3获取失败失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutexByXLock(id);
            }
            //4.4获取锁成功，根据id查询数据库
            //注意：获取锁成功之后应再次查询redis缓存是否存在，如果存在则无需重建缓存
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            shop = getById(id);

            //模拟重建延时（模拟 让更多的线程获取不到锁因此而阻塞）
            Thread.sleep(200);
            //5.判断商铺是否存在，不存在返回错误
            if (shop == null) {
                //不存在，将空值写入redis缓存
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.商铺存在则将商铺数据写入Redis，设置过期时间30分钟
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        //8.返回
        return shop;
    }

    /**
     * 获取互斥锁（Redis的String数据结构模拟）
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        //setnx key value 当key不存在的时候设置value值
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //flag是boolean类型的，但不能直接返回，因为直接返回会有拆箱操作，拆箱过程中会有空值
        //拆箱底层就是调用booleanValue()方法，如果flag为null就会空指针
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 向redis中写入店铺信息并设置过期时间
     * @param id
     * @param expireSeconds
     * @throws InterruptedException
     */
    public void saveShopWithExpireTime(Long id,Long expireSeconds) throws InterruptedException {
        Thread.sleep(200);
        //1.查询店铺信息
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id , JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存更新策略：先写数据库，再删除缓存
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public ResultUtils update(Shop shop) {
        //加@TransActional 可以实现缓存与数据库的同步更新（数据一致性）
        //先对商铺id进行判断
        Long id = shop.getId();
        if (id == null) {
            return ResultUtils.fail("店铺id不能为null");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存，key是上面存入的key；但要对id进行判断
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        //3.返回
        return ResultUtils.ok();
    }

    @Override
    public ResultUtils queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要使用坐标查询
        if (x == null || y == null) {
            //不需要坐标查询，按照数据库查询
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return ResultUtils.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //3.查询redis，按照距离排序，分页，结果,shopId,distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        //4.解析出id
        if(results == null) {
            return ResultUtils.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from ) {
            //没有下一页；
            return ResultUtils.ok(Collections.emptyList());
        }
        //4.1截取 from - end 的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            //获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });

        //5.根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(ID," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        //6.返回
        return ResultUtils.ok(shops);
    }
}
