package com.lmeng.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lmeng.dto.ResultUtils;
import com.lmeng.model.SeckillVoucher;
import com.lmeng.model.VoucherOrder;
import com.lmeng.mapper.VoucherOrderMapper;
import com.lmeng.service.ISeckillVoucherService;
import com.lmeng.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lmeng.once.RedisWorker;
import com.lmeng.global.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisWorker redisWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;



    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

//    @PostConstruct
//    private void init() {
////        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }

//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    //1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.创建订单
//                    handlerVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//
//        private void handlerVoucherOrder(VoucherOrder voucherOrder) {
//            //1.获取用户
//            Long userId = voucherOrder.getUserId();
//            //2.创建锁对象
//            RLock lock = redissonClient.getLock("lock:order:" + userId);
//            //3.获取锁，为了保证并发安全
//            boolean isLock = lock.tryLock();
//            //4.判断是否获取锁成功
//            if(!isLock) {
//                //获取锁失败，返回错误或重试
//                log.error("不允许重复下单");
//                return ;
//            }
//            try {
//                //拿到线程的对象
//                proxy.createVoucherOrder(voucherOrder);
//            } finally {
//                //释放锁
//                lock.unlock();
//            }
//        }
//    }

    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    private IVoucherOrderService proxy;
    @Override
    @Transactional
    public ResultUtils seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始，开始之间在当前时间之后
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //秒杀未开始
            return ResultUtils.fail("秒杀未开始");
        }

        //3.判断秒杀是否结束，结束时间在当前之间之前
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀已结束
            return ResultUtils.fail("秒杀已结束");
        }

        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不充足
            return ResultUtils.fail("库存不充足");
        }

        //一人一单设置的synchronized锁；
        //先获取锁，在进入方法，方法执行完成，事务提交，再释放锁
        Long userId = UserHolder.getUser().getId();
        synchronized(userId.toString().intern()) {
            //获取IVoucherOrderService当前事务代理的对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //现在事务才可以生效
            return proxy.createVoucherOrder(voucherId);
        }

//        Long userId = UserHolder.getUser().getId();
//        //尝试创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if(!isLock) {
//            //获取锁失败，返回错误或重试
//            return ResultUtils.fail("一个人只允许下一单");
//        }
//        try {
//            //获取事务代理的对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //现在事务才可以生效
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//
//            lock.unlock();
//        }
    }

    /**
     * 创建订单
     * @param voucherId
     */
    @Transactional
    @Override
    public ResultUtils createVoucherOrder(Long voucherId) {
        //5.一人一单
        Long userId = UserHolder.getUser().getId();
        //改为内部加锁；锁对象是用户；先释放锁，再提交事务
        //intern是使让每次加锁的对象相同
        //synchronized(userId.toString().intern())

        //5.1查询用户的订单
        int count = query().eq("user_id", userId)
                            .eq("voucher_id", voucherId)
                            .count();
        //5.2判断用户是否下过单 >1说明已下过单
        if (count > 0) {
            return ResultUtils.fail("每个用户只能购买一次");
        }

        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id",voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return ResultUtils.fail("库存不足");
        }

        //7.创建订单，保存订单信息到订单表
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id，用全局唯一id
        long id = redisWorker.nextId("order");
        voucherOrder.setId(id);
        //用户id
        userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);

        //7.保存新的订单信息到订单表
        this.save(voucherOrder);
        //8.返回
        return ResultUtils.ok(voucherId);
    }

    @Deprecated
    public ResultUtils seckillVoucher1(Long voucherId) {
        //1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),  userId.toString()
        );

        //2.判断结果是否为0
        int r = result.intValue();
        if(r != 0) {
            //2.1不为0，没有购买资格
            return ResultUtils.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2为0，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.3订单id
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.4用户id
        voucherOrder.setUserId(userId);
        //2.5代金券id
        voucherOrder.setVoucherId(voucherId);
        //2.6阻塞队列
        orderTasks.add(voucherOrder);

        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return ResultUtils.ok(orderId);
    }

}
