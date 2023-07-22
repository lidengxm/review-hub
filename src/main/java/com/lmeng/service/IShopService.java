package com.lmeng.service;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface IShopService extends IService<Shop> {

    ResultUtils queryById(Long id);

    ResultUtils update(Shop shop);

    ResultUtils queryShopByType(Integer typeId, Integer current, Double x, Double y);

}

