package com.lmeng.service;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    ResultUtils queryById(Long id);

    ResultUtils update(Shop shop);

    ResultUtils queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
