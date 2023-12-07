package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getAllTypes() {
        String shopTypeList = stringRedisTemplate.opsForValue().get(RedisConstants.TYPE_SHOP_KEY);
        if (StrUtil.isNotEmpty(shopTypeList)) {
            return Result.ok(JSONUtil.toList(shopTypeList, ShopType.class));
        }
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        shopTypeList = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(RedisConstants.TYPE_SHOP_KEY, shopTypeList);
        return Result.ok(shopTypes);
    }

}
