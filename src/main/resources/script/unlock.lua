-- 使用lua脚本解决释放锁过程中的原子性问题
-- 判断一致释放锁
if(redis.call('GET',KEYS[1])==ARGV[1]) then
    return redis.call('DEL',KEYS[1])
else
    return 0