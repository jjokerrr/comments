package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopService shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    ExecutorService executors = Executors.newFixedThreadPool(10);


    @Test
    public void preLoadRedisData() {
        Shop byId = shopService.getById(1);
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(20L));
        redisData.setData(byId);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + 1, JSONUtil.toJsonStr(redisData));

    }

    @Test
    public void getInitTime() {
        LocalDateTime initTime = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        long seconds = initTime.toEpochSecond(ZoneOffset.UTC);
        System.out.printf(String.valueOf(seconds));

    }

    @Test
    public void testMutexId() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        for (int i = 0; i < 300; i++) {
            executors.submit(() -> {
                long order = redisIdWorker.nextId("order");
                System.out.println("id ="+ order);
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();

    }

    @Test
    public void generateId() {
        long order = redisIdWorker.nextId("order");
        System.out.printf("id=" + order);

    }
}
