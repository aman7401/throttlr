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
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final StorageBackend storageBackend;

    @Override
    public RateLimitResult isAllowed(RateLimitRequest request, RateLimitRule rule) {
        // Window ID = which fixed bucket this timestamp falls into
        // e.g. window=60s: all requests in 00:00-00:59 share the same windowId
        long windowId = request.getTimestamp().getEpochSecond() / rule.getWindowSizeSeconds();
        String key = String.format("throttlr:fw:%s:%d", request.getApiKey(), windowId);
        return storageBackend.checkAndIncrement(key, rule, request.getTimestamp());
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.FIXED_WINDOW;
    }
}
