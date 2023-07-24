package com.lmeng.service;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 优惠券下单
     * @param voucherId
     * @return
     */
    ResultUtils seckillVoucherCommon(Long voucherId);

    /**
     * 创建订单
     * @param voucherOrder
     * @return
     */
    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * Lua解决秒杀业务
     * @param voucherId
     * @return
     */
    ResultUtils seckillVoucherLua(Long voucherId);
}
