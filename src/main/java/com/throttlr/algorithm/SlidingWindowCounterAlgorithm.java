package com.throttlr.algorithm;

import com.throttlr.model.AlgorithmType;
import com.throttlr.model.RateLimitRequest;
import com.throttlr.model.RateLimitResult;
import com.throttlr.model.RateLimitRule;
import com.throttlr.storage.StorageBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlidingWindowCounterAlgorithm implements RateLimitAlgorithm {

    private final StorageBackend storageBackend;

    @Override
    public RateLimitResult isAllowed(RateLimitRequest request, RateLimitRule rule) {
        // Base key — the Lua script derives current and previous window sub-keys from this
        // e.g. throttlr:swc:key-abc:28500 and throttlr:swc:key-abc:28499
        String key = String.format("throttlr:swc:%s", request.getApiKey());
        return storageBackend.checkAndIncrement(key, rule, request.getTimestamp());
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.SLIDING_WINDOW_COUNTER;
    }
}
