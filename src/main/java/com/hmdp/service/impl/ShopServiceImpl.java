package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

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
    private CacheClient cacheClient;

    // 创建线程池,创建10个线程的线程池用于加载逻辑过期时间
    ExecutorService executor = Executors.newFixedThreadPool(10);


    //  解决缓存击穿的两种实现方法，使用互斥锁和逻辑过期时间
    @Override
    public Result queryById(Long id) {
//        Shop shopById = getShopById(id);
//        Shop shopById = getByIdWithMutex(id);
//        Shop shopById = getByIdWithLogicTime(id);
        // 使用封装好的工具类
        Shop shopById = cacheClient.queryWirthPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        Shop shopById = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById);
        if (shopById == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shopById);

    }

    @Transactional
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

    @Override
    public List<Shop> queryByType(Integer typeId, Integer current, Double x, Double y) {
        // 如果为传入坐标信息，则普通查找
        // 根据类型分页查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return page.getRecords();
        }

        // 根据坐标经纬度查询坐标
        // 查询页面起始和结束位置
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = (current) * SystemConstants.DEFAULT_PAGE_SIZE;

        // 根据距离查询
        String shopTypeKey = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate
                .opsForGeo()
                .radius(shopTypeKey
                        , new Circle(new Point(x, y), 5000)
                        , RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end));
        if (results == null) {
            return Collections.emptyList();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents = results.getContent();
        // 查到尾页
        if (contents.size() <= from) {
            return Collections.emptyList();
        }
        // 返回前端数据
        List<Long> ids = new ArrayList<>(end - from + 1);
        Map<Long, Distance> distanceMap = new HashMap<>(end - from + 1);
        contents.stream().skip(from).forEach(geoLocationGeoResult -> {
            Long shopId = Long.valueOf(geoLocationGeoResult.getContent().getName());
            ids.add(shopId);
            Distance distance = geoLocationGeoResult.getDistance();
            distanceMap.put(shopId, distance);
        });
        String idStr = StrUtil.join(",", ids);
        List<Shop> list = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : list) {
            shop.setDistance(distanceMap.get(shop.getId()).getValue());
        }
        return list;
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
        ILock redisLock = new RedisLock(stringRedisTemplate);
        Shop shop = null;
        try {
            if (!redisLock.tryLock(RedisConstants.LOCK_SHOP_KEY + id, RedisConstants.LOCK_SHOP_TTL)) {
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
            redisLock.unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    // 使用逻辑过期来解决缓存击穿问题，使用逻辑过期的热点key问题需要提前手动预载热点key进入到缓存中，使热点数据常驻缓存
    private Shop getByIdWithLogicTime(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isEmpty(shopJson)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 数据未过期，直接返回
            return shop;
        }
        // 尝试获取互斥锁
        ILock redisLock = new RedisLock(stringRedisTemplate);
        if (redisLock.tryLock(RedisConstants.LOCK_SHOP_KEY + id, RedisConstants.LOCK_SHOP_TTL)) {
            try {
                executor.submit(() -> {
                    Shop byId = getById(id);
                    RedisData shopData = new RedisData();
                    shopData.setData(byId);
                    shopData.setExpireTime(LocalDateTime.now().plusSeconds(10L));
                    // 热点key不设置过期时间
                    stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shopData));

                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                redisLock.unLock(RedisConstants.LOCK_SHOP_KEY + id);
            }
        }

        // 未获取互斥锁的对象直接将旧的缓存对象返回，会存在短期的数据不一致问题
        return shop;
    }


}

