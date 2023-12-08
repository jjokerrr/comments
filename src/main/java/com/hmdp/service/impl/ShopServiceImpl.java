package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisLock redisLock;

    // TODO 解决缓存击穿的两种实现方法，使用互斥锁和逻辑过期时间
    @Override
    public Result queryById(Long id) {
//        Shop shopById = getShopById(id);
        Shop shopById = getByIdWithMutex(id);
        if (shopById == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shopById);

    }

    @Override
    public Result updateShopById(Shop shop) {
        // 实现更新缓存的功能，为保证一致性采用更新是删除缓存的策略
        // 数据校验
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("输入数据格式错误");
        }

        boolean isUpdated = updateById(shop);
        if (!isUpdated) {
            return Result.fail("数据写入失败");
        }

        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();

    }

    /*
     * 解决缓存穿透和缓存雪崩问题
     * @Parameter [id]
     * @Return Shop
     */
    private Shop getShopById(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotEmpty(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if (shopJson != null) {
            return null;
        }
        Shop shop = getById(id);
        if (shop == null) {
            // 查询失败，为防止缓存穿透问题，通过使用缓存空值来解决缓存穿透问题
            // 缓存穿透问题：缓存和数据库均不存在该数据。解决办法：1.缓存空值 2布隆过滤
            // 缓存空值，这里为了方便调试，设置了较长时间的缓存时间
            stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        String shopStr = JSONUtil.toJsonStr(shop);
        Long randomTime = RandomUtil.randomLong(1, 10);
        // 通过设置一个随机的缓存时间来避免缓存雪崩问题
        stringRedisTemplate.opsForValue().set(shopKey, shopStr, RedisConstants.CACHE_SHOP_TTL + randomTime, TimeUnit.MINUTES);
        return shop;
    }

    /*
     * 使用互斥锁解决缓存击穿问题，解决缓存穿透和缓存雪崩问题
     * @Parameter [id]
     * @Return Shop
     */
    private Shop getByIdWithMutex(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotEmpty(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if (shopJson != null) {
            return null;
        }

        Shop shop = null;
        try {
            if (!redisLock.tryLock(RedisConstants.LOCK_SHOP_KEY, RedisConstants.LOCK_SHOP_TTL)) {
                // 休眠重试
                Thread.sleep(50);
                return getByIdWithMutex(id);
            }

            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            String shopStr = JSONUtil.toJsonStr(shop);
            Long randomTime = RandomUtil.randomLong(1, 10);
            // 通过设置一个随机的缓存时间来避免缓存雪崩问题
            stringRedisTemplate.opsForValue().set(shopKey, shopStr, RedisConstants.CACHE_SHOP_TTL + randomTime, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            redisLock.unLock(RedisConstants.LOCK_SHOP_KEY);
        }
        return shop;
    }
}

