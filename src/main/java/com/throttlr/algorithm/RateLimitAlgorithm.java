package com.throttlr.algorithm;

import com.throttlr.model.AlgorithmType;
import com.throttlr.model.RateLimitRequest;
import com.throttlr.model.RateLimitResult;
import com.throttlr.model.RateLimitRule;

public interface RateLimitAlgorithm {

    /**
     * Each implementation:
     * 1. Builds a Redis key specific to its algorithm and the API key
     * 2. Delegates to StorageBackend.checkAndIncrement() which fires the Lua script
     */
    RateLimitResult isAllowed(RateLimitRequest request, RateLimitRule rule);

    AlgorithmType getType();
}
