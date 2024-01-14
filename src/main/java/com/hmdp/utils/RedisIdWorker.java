package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    final long BEGIN_TIMESTAMP = 946684800L;      // 2000-01-01-00:00:00时间
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public long nextId(String prefix) {
        // 生成时间戳
        LocalDateTime currentTime = LocalDateTime.now();
        long timeStamp = currentTime.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        // 生成当前序列号
        // 获取当前月份
        String currentMonth = currentTime.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        String key = "icr:" + prefix + ":" + currentMonth;
        long increment = stringRedisTemplate.opsForValue().increment(key);

        // 生成id
        return (timeStamp << 32) | increment;
    }


}
