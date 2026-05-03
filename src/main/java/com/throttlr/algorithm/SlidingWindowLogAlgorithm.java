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
public class SlidingWindowLogAlgorithm implements RateLimitAlgorithm {

    private final StorageBackend storageBackend;

    @Override
    public RateLimitResult isAllowed(RateLimitRequest request, RateLimitRule rule) {
        // Single sorted-set key per API key — the Lua script manages timestamps inside it
        String key = String.format("throttlr:swl:%s", request.getApiKey());
        return storageBackend.checkAndIncrement(key, rule, request.getTimestamp());
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.SLIDING_WINDOW_LOG;
    }
}
