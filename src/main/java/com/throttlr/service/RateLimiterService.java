package com.throttlr.service;

import com.throttlr.algorithm.AlgorithmFactory;
import com.throttlr.config.RateLimiterProperties;
import com.throttlr.model.AlgorithmType;
import com.throttlr.model.RateLimitRequest;
import com.throttlr.model.RateLimitResult;
import com.throttlr.model.RateLimitRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final AlgorithmFactory algorithmFactory;
    private final RateLimiterProperties properties;
    private final RuleStore ruleStore;
    private final MeterRegistry meterRegistry;

    public RateLimitResult checkRateLimit(String apiKey) {
        RateLimitRule rule = resolveRule(apiKey);

        RateLimitRequest request = RateLimitRequest.builder()
                .apiKey(apiKey)
                .timestamp(Instant.now())
                .build();

        try {
            RateLimitResult result = algorithmFactory.get(rule.getAlgorithm()).isAllowed(request, rule);
            recordMetric(apiKey, rule.getAlgorithm(), result.isAllowed());
            return result;
        } catch (Exception e) {
            log.error("Rate limiter error for key [{}]: {}", apiKey, e.getMessage());
            boolean failOpen = properties.isFailOpen();
            recordMetric(apiKey, rule.getAlgorithm(), failOpen);
            return failOpen
                    ? RateLimitResult.allowed(rule.getLimit(), rule.getLimit(), 0)
                    : RateLimitResult.denied(rule.getLimit(), 0);
        }
    }

    /**
     * Rule resolution order:
     * 1. Redis  — dynamic rules saved via admin API (survive restarts)
     * 2. YAML   — static rules from application.yml
     * 3. Default — fallback for unknown API keys
     */
    private RateLimitRule resolveRule(String apiKey) {
        return ruleStore.findByApiKey(apiKey)
                .or(() -> properties.getRules().stream()
                        .filter(r -> apiKey.equals(r.getApiKey()))
                        .findFirst())
                .map(this::toRateLimitRule)
                .orElseGet(() -> buildDefaultRule(apiKey));
    }

    private void recordMetric(String apiKey, AlgorithmType algorithm, boolean allowed) {
        Counter.builder("throttlr.requests.total")
                .description("Total rate limit decisions")
                .tag("apiKey", apiKey)
                .tag("algorithm", algorithm.name().toLowerCase())
                .tag("result", allowed ? "allowed" : "denied")
                .register(meterRegistry)
                .increment();
    }

    private RateLimitRule toRateLimitRule(RateLimiterProperties.RuleConfig r) {
        return RateLimitRule.builder()
                .apiKey(r.getApiKey())
                .limit(r.getLimit())
                .windowSizeSeconds(r.getWindowSizeSeconds())
                .algorithm(r.getAlgorithm())
                .build();
    }

    private RateLimitRule buildDefaultRule(String apiKey) {
        RateLimiterProperties.RuleConfig def = properties.getDefaultRule();
        int limit          = def != null ? def.getLimit()             : 100;
        int window         = def != null ? def.getWindowSizeSeconds() : 60;
        AlgorithmType algo = def != null ? def.getAlgorithm()         : AlgorithmType.FIXED_WINDOW;

        return RateLimitRule.builder()
                .apiKey(apiKey)
                .limit(limit)
                .windowSizeSeconds(window)
                .algorithm(algo)
                .build();
    }
}
