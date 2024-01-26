

local voucherId = ARGV[1]

local userId = ARGV[2]

local stockKey  = 'seckill:stock:' .. voucherId

local orderKey = 'seckill:order:' .. userId

-- 检查剩余优惠券数量

if (tonumber(redis.call('GET', stockKey)) <= 0) then
    return 1
end

-- 重复下单校验
if (redis.call('SISMEMBER', orderKey, userId) == 1 ) then
        return 2
end

-- 执行下单操作
redis.call('INCRBY', stockKey, -1)
redis.call('SADD', orderKey, userId)
return 0