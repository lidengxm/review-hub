package com.lmeng.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    /**
     * Lua脚本秒杀优惠券
     *
     * @param voucherId
     * @return
     */
    @Override
    public ResultUtils seckillVoucherLua(Long voucherId) {
        //1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        //订单id
        long orderId = redisWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.1不为0，没有购买资格
            return ResultUtils.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return ResultUtils.ok(orderId);
    }

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //获取消息队列中的信息
    private class VoucherOrderHandler implements Runnable {
        String queryName = "stream.order";

        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取消息队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams streams.order
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queryName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息是否存在
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息队列中的信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4.获取成功，可以下订单
                    handlerVoucherOrder(voucherOrder);
                    //5.ack确认  sack stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queryName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    //处理异常消息
                    handlerPendingList();
                }
            }
        }

        //处理异常消息并确认消息
        private void handlerPendingList() {
            while (true) {
                try {
                    //1.获取消息队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams streams.order
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queryName, ReadOffset.from("0"))
                    );
                    //2.判断消息是否存在
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明pending-list没有消息，结束循环
                        break;
                    }
                    //3.解析消息队列中的信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4.获取成功，可以下订单
                    handlerVoucherOrder(voucherOrder);
                    //5.ack确认  sack stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queryName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户，只能从订单中获取
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁，为了保证并发安全
        boolean isLock = lock.tryLock();
        //4.判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误或重试
            log.error("不允许重复下单");
            return;
        }
        try {
            //拿到线程的对象
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }


    /**
     * 创建订单
     *
     * @param voucherOrder
     */
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userId = voucherOrder.getId();
        //改为内部加锁；锁对象是用户；先释放锁，再提交事务
        //intern是使让每次加锁的对象相同
        //synchronized(userId.toString().intern())

        //5.1查询用户的订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        //5.2判断用户是否下过单 >1说明已下过单
        if (count > 0) {
            log.error("每个用户只能购买一次");
        }

        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
        }
        //7.保存新的订单信息到订单表
        this.save(voucherOrder);
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public ResultUtils seckillVoucherCommon(Long voucherId) {
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

        //一人一单synchronized锁=>分布式锁实现
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //尝试创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误或重试
            return ResultUtils.fail("一个人只允许下一单");
        }
        try {
            //获取事务代理的对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //现在事务才可以生效
            proxy.createVoucherOrder(new VoucherOrder());
            return ResultUtils.ok(voucherId);
        } finally {
            lock.unlock();
        }
    }
}
