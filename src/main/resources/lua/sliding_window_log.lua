-- Sliding Window Log
-- Stores a timestamp for every request in a sorted set.
-- Most accurate algorithm — no boundary burst problem.
-- Weakness: memory grows with request volume (one entry per request).
--
-- KEYS[1] = throttlr:swl:{apiKey}
-- ARGV[1] = limit
-- ARGV[2] = windowSizeSeconds
-- ARGV[3] = now in milliseconds
-- Returns: {allowed(1/0), remaining, resetTimeSeconds}

local key    = KEYS[1]
local limit  = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now    = tonumber(ARGV[3])

-- Remove all timestamps older than the current window
local windowStart = now - (window * 1000)
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

local count = redis.call('ZCARD', key)

if count >= limit then
    -- Reset time = when the oldest entry will fall outside the window
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local resetTime = window
    if #oldest >= 2 then
        resetTime = math.ceil((tonumber(oldest[2]) + window * 1000 - now) / 1000)
    end
    return {0, 0, math.max(1, resetTime)}
end

-- Use a sequence key to guarantee uniqueness for same-millisecond requests
local seqKey = key .. ':seq'
local seq    = redis.call('INCR', seqKey)
redis.call('EXPIRE', seqKey, window + 1)

local member = tostring(now) .. ':' .. tostring(seq)
redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, window + 1)

return {1, limit - count - 1, window}
