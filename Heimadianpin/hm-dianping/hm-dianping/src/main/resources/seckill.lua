local voucherId = ARGV[1]
local userId = ARGV[2]
local stock = 'seckill:' .. voucherId
local order = 'order:' .. voucherId
if tonumber(redis.call('get', stock)) <= 0 then
    return 1
end
if (redis.call('sismember', order, userId)==1) then
    -- 已经购买
    return 2
end
redis.call()