-- Fixed Window Counter
-- Divides time into fixed buckets (e.g. every 60s). Counts requests per bucket.
-- Simple and fast. Weakness: burst at window boundary (e.g. 10 at 00:59 + 10 at 01:00 = 20 in 1s).
--
-- KEYS[1] = throttlr:fw:{apiKey}:{windowId}
-- ARGV[1] = limit
-- ARGV[2] = windowSizeSeconds
-- Returns: {allowed(1/0), remaining, ttlSeconds}

local key    = KEYS[1]
local limit  = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local count = redis.call('INCR', key)

-- Set expiry only on first request so we don't keep resetting the window
if count == 1 then
    redis.call('EXPIRE', key, window)
end

local ttl = redis.call('TTL', key)

if count > limit then
    return {0, 0, ttl}
end

return {1, limit - count, ttl}
