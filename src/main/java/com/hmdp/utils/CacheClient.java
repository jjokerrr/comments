package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    private final ExecutorService executor = Executors.newFixedThreadPool(10);


    // 基础存储redis string类型的方法
    public void set(String key, Object value, Long expireTime, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, unit);
    }


    // 存储逻辑过期时间对象
    public void setLogicalExpiration(String key, Object value, Long expireTime, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }

    /*
     * 避免缓存穿透和缓存雪崩问题，通过缓存空值对象和设置随机过期时间策略
     * @Parameter [keyPrefix, id, type, function, expireTime, unit]
     * @Return R
     */
    public <R, L> R queryWirthPassThrough(String keyPrefix, L id, Class<R> type, Function<L, R> function, Long expireTime, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }

        if (json != null) {
            return null;
        }
        R res = function.apply(id);
        if (res == null) {
            // 查询失败，将空值存储在缓存中来避免缓存穿透问题
            this.set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }

        json = JSONUtil.toJsonStr(res);
        Long randomTime = RandomUtil.randomLong(1, 5);
        // 通过设置一个随机的缓存时间来避免缓存雪崩问题
        this.set(key, json, expireTime + randomTime, unit);

        return res;
    }


    // 使用缓存击穿热点key问题需要预载缓存
    public <R, L> R queryWithMutex(String keyPrefix, L id, Class<R> type, Function<L, R> function) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isEmpty(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R res = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expire = redisData.getExpireTime();
        if (expire.isAfter(LocalDateTime.now())) {
            // 数据未过期，直接返回
            return res;
        }
        // 尝试获取互斥锁
        ILock redisLock = new RedisLock(stringRedisTemplate);
        String lockKey = RedisConstants.LOCK_PREFIX + key;
        if (redisLock.tryLock(lockKey, RedisConstants.LOCK_TTL)) {
            try {
                executor.submit(() -> {
                    R byId = function.apply(id);
                    RedisData data = new RedisData();
                    data.setData(byId);
                    data.setExpireTime(LocalDateTime.now().plusSeconds(10L));
                    // 热点key不设置过期时间
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                redisLock.unLock(lockKey);
            }
        }
        return res;
    }


}
