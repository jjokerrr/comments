package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
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

    @Override
    public Result queryById(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);
        Shop shop = new Shop();
        if (!shopMap.isEmpty()) {
            BeanUtil.fillBeanWithMap(shopMap, shop, false);
            return Result.ok(shop);
        }
        shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        Map<String, Object> map = BeanUtil.beanToMap(shop, new HashMap<>()
                , CopyOptions
                        .create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : null));

        stringRedisTemplate.opsForHash().putAll(shopKey, map);
        stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
}

