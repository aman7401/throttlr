# High Level Design — Throttlr

## System Architecture

```
Client
  │  X-API-Key header
  ▼
RateLimitFilter
  │  skip /actuator, /admin
  ▼
RateLimiterService  ──→  resolveRule: Redis → YAML → Default
  │
  ▼
Algorithm  ──→  builds Redis key
  │
  ▼
RedisStorage  ──→  executes Lua script (atomic)
  │
  ▼
ALLOW (200) / DENY (429)
```

---

## Why Redis + Lua

Without atomicity, two app instances can both read the same counter value and both allow a request that should be denied — a classic race condition.

Lua scripts run atomically on Redis's single thread. One script = one decision, guaranteed. No locks, no two round trips.

---

## Rule Resolution

```
1. Redis hash  →  dynamic rules created via Admin API (survive restarts)
2. YAML config →  static rules in application.yml
3. Default     →  100 req / 60s / Fixed Window
```

---

## Failure Handling

| Redis state | `fail-open: true` | `fail-open: false` |
|---|---|---|
| Down / timeout | Allow all | Block all |

---

## Scalability

All counters live in Redis, not in app memory. Any number of app instances share the same state automatically — no coordination needed.

```
Load Balancer
  ├── App 1
  ├── App 2   →  Redis (shared counters)
  └── App 3
```
