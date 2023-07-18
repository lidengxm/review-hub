package com.hmdp.service;

import com.hmdp.dto.ResultUtils;
import com.hmdp.model.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {


    ResultUtils seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
