package com.lmeng.service;

import com.lmeng.dto.ResultUtils;
import com.lmeng.model.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    ResultUtils queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);


}
