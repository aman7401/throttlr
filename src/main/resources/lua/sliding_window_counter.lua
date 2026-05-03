-- Sliding Window Counter
-- Approximates a sliding window using two adjacent fixed windows.
-- Formula: count = currentWindowCount + previousWindowCount * (1 - elapsedFraction)
-- Good balance of accuracy vs memory. Uses only 2 counters instead of full log.
--
-- KEYS[1] = throttlr:swc:{apiKey}  (base key — script derives current/previous keys)
-- ARGV[1] = limit
-- ARGV[2] = windowSizeSeconds
-- ARGV[3] = now in milliseconds
-- Returns: {allowed(1/0), remaining, resetTimeSeconds}

local baseKey  = KEYS[1]
local limit    = tonumber(ARGV[1])
local window   = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local windowMs       = window * 1000
local currentWinId   = math.floor(now / windowMs)
local currentKey     = baseKey .. ':' .. tostring(currentWinId)
local previousKey    = baseKey .. ':' .. tostring(currentWinId - 1)

local currentCount  = tonumber(redis.call('GET', currentKey)  or '0') or 0
local previousCount = tonumber(redis.call('GET', previousKey) or '0') or 0

-- How far into the current window we are (0.0 → 1.0)
local elapsedInWindow = now - (currentWinId * windowMs)
local previousWeight  = 1.0 - (elapsedInWindow / windowMs)
local estimatedCount  = currentCount + math.floor(previousCount * previousWeight)

local resetTime = math.ceil((windowMs - elapsedInWindow) / 1000)

if estimatedCount >= limit then
    return {0, 0, resetTime}
end

local newCount = redis.call('INCR', currentKey)
if newCount == 1 then
    -- Keep key alive long enough for the next window to use it as "previous"
    redis.call('EXPIRE', currentKey, window * 2)
end

return {1, limit - estimatedCount - 1, resetTime}
