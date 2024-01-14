package com.hmdp.service.impl;


import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker; // 使用redis全局id生成器创建订单ID

    @Override
    @Transactional
    public long order(SeckillVoucher seckillVoucher) {
        // 检查库存数量，这个必须放在事务中，防止并发问题导致的失败
        if (seckillVoucher.getStock()<1) {
            return  -1;
        }
        // 更新库存
        seckillVoucher.setStock(seckillVoucher.getStock()-1);
        updateById(seckillVoucher);

        // 创建订单
        long voucherId = redisIdWorker.nextId("vocherOrder");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(voucherId);

        // 保存
        voucherOrderService.save(voucherOrder);

        return voucherId;


    }
}
