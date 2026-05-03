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
public class LeakyBucketAlgorithm implements RateLimitAlgorithm {

    private final StorageBackend storageBackend;

    @Override
    public RateLimitResult isAllowed(RateLimitRequest request, RateLimitRule rule) {
        // Single hash key per API key — stores { queue, last_leak } in Redis
        String key = String.format("throttlr:lb:%s", request.getApiKey());
        return storageBackend.checkAndIncrement(key, rule, request.getTimestamp());
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.LEAKY_BUCKET;
    }
}
