-- @params KEYS: 1 -> voucher stock Key 2 -> having ordered user key ARGV: 1 -> user's id 2 -> orderId
-- @return Integer 0 -> ok 1 -> no stock 2 -> duplicate order

local voucherId = KEYS[1] -- seckill:stock:12
local orderedKey = KEYS[2] -- seckill:having_ordered:12
local userId = ARGV[1] -- 1010
local id = ARGV[2]

-- 获取库存
local stockRaw = redis.call('get', 'seckill:stock:'..voucherId)
if (not stockRaw) then
    return 3
end

local stock = tonumber(stockRaw)

-- 检查是否有库存
if (stock <= 0) then
    -- 没有，返回1
    return 1

-- 检查是否已下单
elseif (redis.call('sismember', orderedKey, userId) > 0) then
    -- 已下单，返回2
    return 2
else
    -- 扣减库存
    redis.call('incrby', 'seckill:stock:'..voucherId, -1)

    -- 记录下单用户
    redis.call('sadd', orderedKey, userId)

    -- 发送消息
    redis.call('xadd', 'stream.orders', '*', 'voucherId', voucherId, 'userId', userId, 'id', id)

    -- 下单成功，返回0
    return 0
end
