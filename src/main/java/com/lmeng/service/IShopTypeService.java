package com.lmeng.service;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    ResultUtils queryTypeList();
}
