-- Leaky Bucket
-- Requests enter a queue (bucket). The queue drains at a fixed rate regardless of burst.
-- Smoothest output rate — good for downstream services that can't handle bursts.
-- e.g. limit=10, window=60s → drains 1 request every 6 seconds.
--
-- KEYS[1] = throttlr:lb:{apiKey}
-- ARGV[1] = capacity (max queue size = limit)
-- ARGV[2] = windowSizeSeconds (full bucket drains in this time)
-- ARGV[3] = now in milliseconds
-- Returns: {allowed(1/0), remaining, resetTimeSeconds}

local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local window   = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

-- Requests that leak (drain) per millisecond
local leakRatePerMs = capacity / (window * 1000)

local data      = redis.call('HMGET', key, 'queue', 'last_leak')
local queueSize = tonumber(data[1])
local lastLeak  = tonumber(data[2])

-- First ever request: bucket was empty, add this request
if queueSize == nil or lastLeak == nil then
    redis.call('HMSET', key, 'queue', '1', 'last_leak', tostring(now))
    redis.call('EXPIRE', key, window * 2)
    return {1, capacity - 1, 0}
end

-- Drain requests that have leaked since last check
local elapsed = now - lastLeak
local leaked  = elapsed * leakRatePerMs
queueSize = math.max(0, queueSize - leaked)

if queueSize >= capacity then
    -- Time until one slot opens up
    local msUntilSlot = math.ceil((queueSize - capacity + 1) / leakRatePerMs)
    return {0, 0, math.max(1, math.ceil(msUntilSlot / 1000))}
end

queueSize = queueSize + 1
redis.call('HMSET', key, 'queue', tostring(queueSize), 'last_leak', tostring(now))
redis.call('EXPIRE', key, window * 2)

return {1, capacity - math.floor(queueSize), 0}
