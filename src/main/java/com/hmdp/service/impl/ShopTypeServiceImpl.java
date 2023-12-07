package com.hmdp.service.impl;

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
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(RedisConstants.TYPE_SHOP_KEY, 0, -1);
        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            return Result.ok(parseStr2Obj(shopTypeList));
        }
        shopTypeList = query().orderByAsc("sort")
                .list()
                .stream()
                .map((JSONUtil::toJsonStr))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.TYPE_SHOP_KEY, shopTypeList);

        return Result.ok(parseStr2Obj(shopTypeList));
    }

    private List<ShopType> parseStr2Obj(List<String> lstr) {
        return lstr.stream()
                .map((item -> (ShopType) JSONUtil.toBean(item, ShopType.class, false)))
                .collect(Collectors.toList());
    }
}
