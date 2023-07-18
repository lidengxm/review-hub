package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.ResultUtils;
import com.hmdp.model.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public ResultUtils queryTypeList() {
        //List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        //1.从redis中查询店铺类型缓存
        String shopType = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        //2.判断缓存是否存在？
        if (StrUtil.isNotBlank(shopType)) {
            //3.若存在就直接返回
            //List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
            //return Result.ok(shopTypes);
            return ResultUtils.ok(JSONUtil.toList(shopType, ShopType.class));
        }

        //4.不存在则查询数据库
        List<ShopType> shopTypes = this.query().orderByAsc("sort").list();

        //5.如果查询的商铺类型为空或不存在
        if (shopTypes == null || shopTypes.size() == 0) {
            return ResultUtils.fail("商铺类型查询失败!");
        }

        //6.将商铺类型数据写入到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopTypes));
        //7.返回
        return ResultUtils.ok(shopTypes);
    }
}
