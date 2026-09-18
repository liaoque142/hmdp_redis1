package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始");
        }

        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束");
        }


        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        return creatVoucherOrder(voucherId);
    }


    //对下面业务操作进行封装，ctrl+alt+m
    //synchronized 是 Java 的同步关键字（加锁），作用是：同一时刻，只允许一个线程执行这个方法，其他线程必须排队等待。
    //@Transactional 是什么 Spring 声明式事务，自动开启/提交/回滚
    //解决什么问题 保证多个数据库操作要么全成功，要么全回滚（原子性）
    @NonNull
    @Transactional
    public synchronized Result creatVoucherOrder(Long voucherId) {
        // 4.1 一人一单
        Long userId = UserHolder.getUser().getId();

        // 4.1.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        // 4.1.2 判断是否存在
        if (count > 0) {
            //用户已经购买过了
            return Result.fail("用户已经购买过了！");
        }

        // 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock = ?
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2用户id
        voucherOrder.setUserId(userId);
        // 6.3代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 6.4返回订单id

        // 7.返回对象
        return Result.ok(orderId);
    }
}
