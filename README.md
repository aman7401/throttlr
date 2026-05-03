# Throttlr

> A production-ready **Distributed Rate Limiter** built with Java, Spring Boot, and Redis Lua scripts.

[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=java)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis)](https://redis.io)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://www.docker.com)

---

## How it works

```
HTTP Request
      ↓
RateLimitFilter  ←  extracts X-API-Key header
      ↓
RateLimiterService  ←  resolves rule (Redis → YAML → default)
      ↓
Algorithm  ←  builds Redis key
      ↓
Lua Script on Redis  ←  atomic check + increment
      ↓
ALLOW (200) or DENY (429)
```

Every rate limit decision is a **single atomic Lua script** on Redis — no race conditions, no two round trips.

---

## Algorithms

| Algorithm | Best For | Memory |
|---|---|---|
| **Fixed Window** | Simple, lowest overhead | O(1) |
| **Sliding Window Log** | Most accurate, no boundary burst | O(n) per key |
| **Sliding Window Counter** | Balanced — accurate + low memory | O(1) |
| **Token Bucket** | APIs that allow short bursts | O(1) |
| **Leaky Bucket** | Smoothest output, protects downstream | O(1) |

---

## Quick Start

```bash
git clone https://github.com/aman7401/throttlr.git
cd throttlr
docker-compose up
```

App starts on `http://localhost:8080`.

---

## Configuration

Rules are defined in `application.yml` and can be updated at runtime via the Admin API.

```yaml
throttlr:
  fail-open: true          # true = allow all if Redis is down, false = block all
  default-rule:
    limit: 100
    window-size-seconds: 60
    algorithm: FIXED_WINDOW
  rules:
    - api-key: "key-basic"
      limit: 10
      window-size-seconds: 60
      algorithm: FIXED_WINDOW
    - api-key: "key-token"
      limit: 10
      window-size-seconds: 60
      algorithm: TOKEN_BUCKET
```

---

## API Reference

### Rate-limited endpoints (require `X-API-Key` header)

```bash
GET  /api/hello
GET  /api/data
POST /api/process
```

Every response includes standard rate limit headers:
```
X-RateLimit-Limit:     10
X-RateLimit-Remaining: 7
X-RateLimit-Reset:     42
```

### Admin endpoints

```bash
# View all rules (dynamic from Redis + static from YAML)
GET /admin/rules

# Get rule for one key
GET /admin/rules/{apiKey}

# Create a new rule (persisted in Redis — survives restarts)
POST /admin/rules
Content-Type: application/json
{ "apiKey": "key-new", "limit": 50, "windowSizeSeconds": 60, "algorithm": "SLIDING_WINDOW_COUNTER" }

# Update an existing rule
PUT /admin/rules/{apiKey}
{ "limit": 200, "windowSizeSeconds": 60, "algorithm": "TOKEN_BUCKET" }

# Remove a dynamic rule (YAML rule takes over if configured)
DELETE /admin/rules/{apiKey}

# Reset rate limit counters for a key (without removing the rule)
DELETE /admin/reset/{apiKey}

# Health check
GET /admin/health
```

### 429 response format

```json
{ "error": "Rate limit exceeded", "resetInSeconds": 42 }
```

---

## Metrics

Prometheus metrics are exposed at `GET /actuator/prometheus`.

| Metric | Tags | Description |
|---|---|---|
| `throttlr_requests_total` | `apiKey`, `algorithm`, `result` | Every rate limit decision |
| `http_server_requests_seconds` | `status`, `uri` | All HTTP requests (built-in) |

Example Prometheus query to find top throttled keys:
```
topk(5, sum by (apiKey) (throttlr_requests_total{result="denied"}))
```

---

## Running Tests

```bash
# Unit tests (no Redis needed)
mvn test -Dtest=AlgorithmKeyTest

# Integration tests (requires Docker for Testcontainers)
mvn test -Dtest=RateLimiterIntegrationTest

# All tests
mvn test
```

---

## Project Structure

```
src/main/java/com/throttlr/
├── algorithm/        # 5 algorithm implementations + factory
├── config/           # Redis config + YAML properties mapping
├── controller/       # DemoController + AdminController
├── filter/           # RateLimitFilter (OncePerRequestFilter)
├── model/            # RateLimitRule, RateLimitResult, RateLimitRequest
├── service/          # RateLimiterService, ApiKeyService, RuleStore
├── storage/          # StorageBackend interface + RedisStorage
└── exception/        # RateLimitExceededException

src/main/resources/lua/
├── fixed_window.lua
├── sliding_window_log.lua
├── sliding_window_counter.lua
├── token_bucket.lua
└── leaky_bucket.lua
```
