package com.throttlr.storage;

import com.throttlr.model.RateLimitResult;
import com.throttlr.model.RateLimitRule;

import java.time.Instant;

public interface StorageBackend {
    RateLimitResult checkAndIncrement(String key, RateLimitRule rule, Instant now);
    void reset(String key);
}
