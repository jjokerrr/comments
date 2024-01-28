-- 传入优惠券id
local voucherId = ARGV[1]
-- 传入用户id
local userId = ARGV[2]
-- 传入订单id
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId

local orderKey = 'seckill:order:' .. userId

-- 检查剩余优惠券数量

if (tonumber(redis.call('GET', stockKey)) <= 0) then
    return 1
end

-- 重复下单校验
if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    return 2
end

-- 执行下单操作，修改Redis中缓存的数据
redis.call('INCRBY', stockKey, -1)
-- 将当前用户添加到set中，避免一人多单问题
redis.call('SADD', orderKey, userId)
-- 将数据添加到消息队列中 XADD key ID field value [field value ...]
redis.call('XADD', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0