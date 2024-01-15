package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RedisLock implements ILock {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String uuid;

    @Override
    public boolean tryLock(String key, Long TTL) {
        // 获取当前线程标识
        uuid = UUID.randomUUID().toString() + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, uuid, TTL, TimeUnit.SECONDS);
        // 这里使用工具类防止自动拆包的时候出现空指针异常
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public boolean unLock(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.equals(uuid)) {
            return false;
        }
        return BooleanUtil.isTrue(stringRedisTemplate.delete(key));
    }

}
