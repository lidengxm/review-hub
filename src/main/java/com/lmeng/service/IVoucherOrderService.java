package com.lmeng.service;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.VoucherOrder;
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

//    void createVoucherOrder(VoucherOrder voucherOrder);
}
