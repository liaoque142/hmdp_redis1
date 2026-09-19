-- 1. 参数列表

-- 1.1 优惠卷id
local voucherId = ARGV[1]

-- 1.2. 用户id
local userId = ARGV[2]

-- 2. 数据key
-- 2.1 库存key
local stockkey = 'seckill:stock' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order' .. voucherId

-- 3. 脚本业务

-- 3.1 判断库存是否充足 get stockkey
if(tonumber(redis.call('get',stockkey)) <= 0) then
    -- 3.2 库存不足，返回1
    return 1
end

-- 3.2 判断用户是否下单 sismember orderKey  userId
if(redis.call('sismember',orderKey,userId) == 1) then
    -- 3.3 存在，说明是重复下单，返回2
    return 2
end
-- 3.4 抠库存 incrby stockkey -1
redis.call('incrby',stockkey,-1)
-- 3.5 下单 (保存用户) sadd orderKey userId
redis.call('sadd',orderKey,userId)


