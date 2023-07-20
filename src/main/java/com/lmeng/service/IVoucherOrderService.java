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
    ResultUtils seckillVoucher(Long voucherId);

    /**
     * 创建订单
     * @param voucherId
     * @return
     */
    ResultUtils createVoucherOrder(Long voucherId);

}
