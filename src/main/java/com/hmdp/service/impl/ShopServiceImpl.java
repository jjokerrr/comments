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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
        return Result.ok(shop);
    }
}
