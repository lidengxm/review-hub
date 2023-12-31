package com.lmeng.controller;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.ShopType;
import com.lmeng.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("/list")
    public ResultUtils queryTypeList() {
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return ResultUtils.ok(typeList);
    }
}
