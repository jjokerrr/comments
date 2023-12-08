package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisLock {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(String key, Long TTL) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "", TTL, TimeUnit.SECONDS);
        // 这里使用工具类防止自动拆包的时候出现空指针异常
        return BooleanUtil.isTrue(flag);
    }

    public boolean unLock(String key) {
        Boolean isDelete = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(isDelete);
    }

}
