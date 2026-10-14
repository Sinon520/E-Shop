-- =====================================================================
-- E-Shop 库存 Lua 原子预扣（防超卖第一层：抗并发入口）
-- KEYS[1] = stock:sku:{skuId}   Redis 中的可售余量（初始由 DB available 预热）
-- ARGV[1] = 本次预扣数量
-- 返回：
--   >= 0 : 预扣成功，返回扣减后的余量
--   -1   : 键不存在（未预热）→ 调用方降级放行，由 DB 乐观锁兜底
--   -2   : 余量不足 → 直接拒绝（3002），不打 DB（抗并发热点的关键）
-- =====================================================================
local stock = redis.call('GET', KEYS[1])
if not stock then
    return -1
end
local available = tonumber(stock)
local quantity = tonumber(ARGV[1])
if available < quantity then
    return -2
end
return redis.call('DECRBY', KEYS[1], quantity)
