package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.HmDianPingApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class RedisLock implements ILock {


    private StringRedisTemplate stringRedisTemplate;

    // 当前线程的唯一标识
    private final String uuid = UUID.randomUUID().toString() + Thread.currentThread().getId();
    // DefaultRedisScript为RedisScript的唯一接口实现类，其中的泛型为返回值类型
    public final static DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 设置脚本独享初始值
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("script/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public RedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(String key, Long TTL) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, uuid, TTL, TimeUnit.SECONDS);
        // 这里使用工具类防止自动拆包的时候出现空指针异常
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public boolean unLock(String key) {
        // 尚存在判断条件和释放锁非原子性操作带来的安全问题，考虑使用lua脚本来解决释放锁
//        String value = stringRedisTemplate.opsForValue().get(key);
//        if (value == null || value.equals(uuid)) {
//            return false;
//        }
//        return BooleanUtil.isTrue(stringRedisTemplate.delete(key));
        Long res = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), uuid);
        return res != null && res.equals(0L);
    }

}
