-- Token Bucket
-- Bucket starts full. Each request consumes 1 token. Tokens refill at a steady rate.
-- Best for APIs that want to allow short bursts while enforcing an average rate.
-- e.g. limit=10, window=60s → refills ~0.167 tokens/sec, allows burst of 10.
--
-- KEYS[1] = throttlr:tb:{apiKey}
-- ARGV[1] = capacity (max tokens = limit)
-- ARGV[2] = windowSizeSeconds (full refill takes this long)
-- ARGV[3] = now in milliseconds
-- Returns: {allowed(1/0), remaining, resetTimeSeconds}

local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local window   = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

-- Tokens added per millisecond
local refillRatePerMs = capacity / (window * 1000)

local data      = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens    = tonumber(data[1])
local lastRefill = tonumber(data[2])

-- First ever request: initialise a full bucket minus this request
if tokens == nil or lastRefill == nil then
    local initial = capacity - 1
    redis.call('HMSET', key, 'tokens', tostring(initial), 'last_refill', tostring(now))
    redis.call('EXPIRE', key, window * 2)
    return {1, initial, 0}
end

-- Refill tokens based on time elapsed since last refill
local elapsed     = now - lastRefill
local tokensToAdd = elapsed * refillRatePerMs
tokens = math.min(capacity, tokens + tokensToAdd)

if tokens < 1 then
    local msUntilToken = math.ceil((1 - tokens) / refillRatePerMs)
    return {0, 0, math.ceil(msUntilToken / 1000)}
end

tokens = tokens - 1
redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(now))
redis.call('EXPIRE', key, window * 2)

return {1, math.floor(tokens), 0}
