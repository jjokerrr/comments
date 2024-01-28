package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.dynamic.scaffold.TypeWriter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker; // 使用redis全局id生成器创建订单ID

    @Resource
    private RedissonClient redissionClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    private final BlockingQueue<VoucherOrder> BLOCK_QUEUE = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService executors = Executors.newFixedThreadPool(3);

    private IVoucherOrderService proxy;


    public final static DefaultRedisScript<Long> SECKILL_CHECK_SCRIPT;

    //
    static {
        SECKILL_CHECK_SCRIPT = new DefaultRedisScript<>();
        SECKILL_CHECK_SCRIPT.setLocation(new ClassPathResource("script/seckillCheck.lua"));
        SECKILL_CHECK_SCRIPT.setResultType(Long.class);
    }


    private final long REPEAT_BUY = -2L;  // 单人重复购买
    private final long SICK_STOCK = -1L;  // 库存数量不足


    @PostConstruct
    private void init() {
        // 使用@PostConstruct注解在创建bean对象的时候启动守护进程
        executors.submit(new VoucherOrderHandler());
    }


    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            String streamKey = "stream.orders";
            String groupName = "g1";
            String consumerName = "c1";
            // 在使用消息队列和消息组之前需要提前预制相关信息。该操作直接在redis客户端完成
            while (true) {
                try {
                    // 从消息队列中取得数据 命令格式 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(groupName, consumerName),
                            StreamReadOptions.empty().block(Duration.ofSeconds(2)).count(1),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
                    // 如果读取为空，那么说明当前消息队列中没有数据，循环指定读取操作
                    if (recordList == null || recordList.isEmpty()) {
                        continue;
                    }

                    // 处理数据，entry中的数据三元组为 id ，key ，value
                    MapRecord<String, Object, Object> entries = recordList.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleOrder(voucherOrder);
                    // 返回确认消息
                    stringRedisTemplate.opsForStream().acknowledge(streamKey, groupName, entries.getId());
                } catch (InterruptedException e) {
                    log.error("插入数据库异常");
                    // 插入异常，重试插入
                    while (true) {

                        try {// 获取Pendlist中的第一位
                            List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                                    Consumer.from(groupName, consumerName),
                                    StreamReadOptions.empty().count(1),
                                    StreamOffset.create(streamKey, ReadOffset.from("0")));
                            // 检查数据是否为空
                            if (recordList == null || recordList.isEmpty()) {
                                break;
                            }
                            // 处理pendlist中的元素
                            // 处理数据，entry中的数据三元组为 id ，key ，value
                            MapRecord<String, Object, Object> entries = recordList.get(0);
                            Map<Object, Object> value = entries.getValue();
                            VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                            // 创建订单
                            handleOrder(voucherOrder);
                            // 返回确认消息
                            stringRedisTemplate.opsForStream().acknowledge("s1", "g1", entries.getId());
                        } catch (Exception error) {
                            // 出现错误，不需要处理等待下一次循环
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                    }
                }
            }
        }
    }

    private void handleOrder(VoucherOrder voucherOrder) throws InterruptedException {
        // 通过锁用户的唯一对象保证每个用户只能购买一份当前优惠券
        Long userId = voucherOrder.getUserId();

        // 使用RedissonClient来获取分布式锁
        RLock lock = redissionClient.getLock(RedisConstants.LOCK_PREFIX + ":order:" + userId);
        // 获取分布式锁,当方法空参的时候，不存在超时释放锁的机制，当输出为三个参数的时候，输入的三个参数分别为等待重试时间，超时时间和时间单位
        if (!lock.tryLock(1, 2, TimeUnit.SECONDS)) {
            // 获取锁失败，返回错误信息
            log.error("插入订单失败");
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放分布式锁,直接使用redisson释放分布式锁方法
            lock.unlock();
        }

    }

    @Override
    public long order(SeckillVoucher seckillVoucher) throws InterruptedException {
        // userId
        long userId = UserHolder.getUser().getId();
        // 订单id
        long orderId = redisIdWorker.nextId("vocherOrder");
        long voucherId = seckillVoucher.getVoucherId();
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 执行数据库脚本，检查是否满足购买条件，满足条件直接放入阻塞队列中
        Long checkRes = stringRedisTemplate
                .execute(SECKILL_CHECK_SCRIPT,
                        Collections.emptyList(),
                        String.valueOf(voucherId),
                        String.valueOf(userId),
                        String.valueOf(orderId)
                );

        if (checkRes != null && checkRes != 0L) {
            return checkRes == 1L ? SICK_STOCK : REPEAT_BUY;
        }

        // 返回全局订单id
        return orderId;


    }


    /*@Override
    public long order(SeckillVoucher seckillVoucher) throws InterruptedException {
        // 执行数据库脚本，检查是否满足购买条件
        Long checkRes = stringRedisTemplate
                .execute(SECKILL_CHECK_SCRIPT,
                        Collections.emptyList(),
                        seckillVoucher.getVoucherId().toString(),
                        UserHolder.getUser().getId().toString());

        if (checkRes != null && checkRes != 0L) {
            return checkRes == 1L ? SICK_STOCK : REPEAT_BUY;
        }

        // 校验成功，创建全局唯一订单id
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("vocherOrder");

        // 创建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setId(orderId);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 将订单对象放到阻塞队列中
        BLOCK_QUEUE.add(voucherOrder);


        // 返回全局订单id
        return orderId;


    }*/

   /* @Override
    public long order(SeckillVoucher seckillVoucher) throws InterruptedException {
        // 检查库存数量，这个必须放在事务中，防止并发问题导致的失败
        if (seckillVoucher.getStock() < 1) {
            return SICK_STOCK;
        }
//        ILock lock = new RedisLock();
        // 通过锁用户的唯一对象保证每个用户只能购买一份当前优惠券
        Long id = UserHolder.getUser().getId();

        // 使用RedissonClient来获取分布式锁
        RLock lock = redissionClient.getLock(RedisConstants.LOCK_PREFIX + ":order:" + id);
        // 获取分布式锁,当方法空参的时候，不存在超时释放锁的机制，当输出为三个参数的时候，输入的三个参数分别为等待重试时间，超时时间和时间单位
        if (!lock.tryLock(1, 2, TimeUnit.SECONDS)) {
            // 获取锁失败，返回错误信息
            return REPEAT_BUY;
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(seckillVoucher);
        } finally {
            // 释放分布式锁,直接使用redisson释放分布式锁方法
            lock.unlock();
        }

//        synchronized (id.toString().intern()) {
//            // 锁和事务那个范围更大一些的问题：锁空间 > 事务空间，防止锁以释放而事务并未提交的问题
//            // 使用代理对象的原因，当使用事务接口的时候说明当前实际执行的方法未代理方法，如果使用原来的方法是达不到想要的效果的
//
//        }


    }*/

    /**
     * createVoucherOrder 同步创建订单方法
     *
     * @Parameter [seckillVoucher]
     * @Return long
     */
    @Transactional
    public long createVoucherOrder(SeckillVoucher seckillVoucher) {
        // 使用锁机制解决一人一单问题
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", seckillVoucher.getVoucherId())
                .count();
        if (count > 0) {
            // 重复购买异常
            return REPEAT_BUY;
        }
        // 更新库存
//        seckillVoucher.setStock(seckillVoucher.getStock()-1);  // 直接更新对象实体会因为线程安全问题导致售出和实际数据库库存的不一致
//        updateById(seckillVoucher);
        // 采用sql语句原子性检查数据库的库存数量和修改库存数量解决超卖问题
        // 当剩余库存数量大于0的时候，即可进行数据库的更新操作
        boolean isSuccessed = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", seckillVoucher.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!isSuccessed) {
            return -1;
        }

        // 创建订单
        long voucherId = redisIdWorker.nextId("vocherOrder");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setUserId(userId);
        voucherOrder.setId(voucherId);

        // 保存
        save(voucherOrder);

        return voucherId;
    }

    /**
     * createVoucherOrder 异步创建订单方法
     *
     * @Parameter [voucherOrder]
     * @Return
     */
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 当剩余库存数量大于0的时候，即可进行数据库的更新操作
        boolean isSuccessed = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!isSuccessed) {
            log.error("插入失败");
        }

        // 保存
        save(voucherOrder);
    }


}
