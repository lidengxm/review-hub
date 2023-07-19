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
import com.lmeng.dto.RedisData;
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

    @Override
    public ResultUtils queryById(Long id) {
        //id2 -> getBy(id2) = this::getById
        //缓存空值解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,
//                                        this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //用互斥锁解决缓存击穿
//        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY,id, Shop.class,
//                                         this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //逻辑过期解决缓存击穿
        //Shop shop = queryWithExpire(id);
        Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY,id,
                Shop.class,this::getById,20L,TimeUnit.MINUTES);

        if(shop == null) {
            return ResultUtils.fail("店铺不存在");
        }
        //返回
        return ResultUtils.ok(shop);
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
