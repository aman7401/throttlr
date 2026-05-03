# Low Level Design — Throttlr

## Package Structure

```
com.throttlr/
├── algorithm/        RateLimitAlgorithm (interface) + 5 implementations + AlgorithmFactory
├── config/           RedisConfig, RateLimiterProperties (@ConfigurationProperties)
├── controller/       DemoController (/api/*), AdminController (/admin/*)
├── filter/           RateLimitFilter (OncePerRequestFilter)
├── model/            AlgorithmType, RateLimitRule, RateLimitRequest, RateLimitResult
├── service/          RateLimiterService, ApiKeyService, RuleStore
├── storage/          StorageBackend (interface), RedisStorage (Lua executor)
└── exception/        RateLimitExceededException
```

---

## Redis Key Design

| Algorithm | Key | Redis Type |
|---|---|---|
| Fixed Window | `throttlr:fw:{apiKey}:{windowId}` | String (counter) |
| Sliding Window Log | `throttlr:swl:{apiKey}` | Sorted Set (timestamps) |
| Sliding Window Counter | `throttlr:swc:{apiKey}` | String (two window counters) |
| Token Bucket | `throttlr:tb:{apiKey}` | Hash `{tokens, last_refill}` |
| Leaky Bucket | `throttlr:lb:{apiKey}` | Hash `{queue, last_leak}` |

`windowId` = `epochSeconds / windowSizeSeconds`  
At t=3660s, window=60s → windowId = **61** (auto-expires, no cleanup needed)

---

## Algorithm Internals

**Fixed Window** — INCR key, EXPIRE on first request. Simple but allows boundary burst (2× limit across window edges).

**Sliding Window Log** — ZREMRANGEBYSCORE to drop expired entries, ZCARD to count, ZADD to log request. Most accurate, O(n) memory.

**Sliding Window Counter** — Weighted estimate: `current + floor(previous × (1 - elapsed/window))`. Accurate at O(1) memory.

**Token Bucket** — Refill tokens based on elapsed time, consume 1 per request. Allows controlled bursts up to capacity.

**Leaky Bucket** — Drain queue based on elapsed time, add 1 per request. Smoothest output rate, best for protecting downstream.

---

## Lua Script Contract

Every script receives the same ARGV and returns the same shape:

```
KEYS[1]  = Redis key
ARGV[1]  = limit
ARGV[2]  = windowSizeSeconds
ARGV[3]  = now (epoch milliseconds)

Returns: {1|0, remaining, resetTimeSeconds}
         1 = allowed, 0 = denied
```

Scripts are loaded at startup via `SCRIPT LOAD` and executed with `EVALSHA` — avoids re-sending the script on every request.

---

## Rule Storage in Redis

Rules are stored as a Redis Hash:

```
Key:    throttlr:config:rules
Field:  {apiKey}
Value:  JSON  →  {"apiKey":"key-x","limit":50,"windowSizeSeconds":60,"algorithm":"TOKEN_BUCKET"}
```

---

## Filter Decision Flow

```
Request URI
  ├── /actuator or /admin  →  pass through (no rate limiting)
  ├── missing X-API-Key    →  401
  └── valid key
        │
        ▼
   checkRateLimit(apiKey)
        │
        ├── set X-RateLimit-Limit / Remaining / Reset headers
        │
        ├── allowed  →  pass to controller
        └── denied   →  429 {"error": "Rate limit exceeded", "resetInSeconds": N}
```
