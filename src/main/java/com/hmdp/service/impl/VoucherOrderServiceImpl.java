package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker; // 使用redis全局id生成器创建订单ID


    @Override
    public long order(SeckillVoucher seckillVoucher) {
        // 检查库存数量，这个必须放在事务中，防止并发问题导致的失败
        if (seckillVoucher.getStock() < 1) {
            return -1;
        }
        ILock lock = new RedisLock();
        // 通过锁用户的唯一对象保证每个用户只能购买一份当前优惠券
        Long id = UserHolder.getUser().getId();
        // 获取分布式锁
        if (!lock.tryLock(RedisConstants.LOCK_PREFIX + ":order:" + id, 10L)) {
            // 获取锁失败，返回错误信息
            return -1;
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(seckillVoucher);
        } finally {
            // 释放分布式锁
            lock.unLock(RedisConstants.LOCK_PREFIX + ":order:" + id);
        }

//        synchronized (id.toString().intern()) {
//            // 锁和事务那个范围更大一些的问题：锁空间 > 事务空间，防止锁以释放而事务并未提交的问题
//            // 使用代理对象的原因，当使用事务接口的时候说明当前实际执行的方法未代理方法，如果使用原来的方法是达不到想要的效果的
//
//        }


    }

    @Transactional
    public long createVoucherOrder(SeckillVoucher seckillVoucher) {
        // 使用锁机制解决一人一单问题
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", seckillVoucher.getVoucherId())
                .count();
        if (count > 0) {
            return -1;
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


}
