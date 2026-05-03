package com.throttlr.config;

import com.throttlr.model.AlgorithmType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the throttlr.* block in application.yml to a typed Java object.
 *
 * Example YAML:
 *   throttlr:
 *     fail-open: true
 *     default-rule:
 *       limit: 100
 *       window-size-seconds: 60
 *       algorithm: FIXED_WINDOW
 *     rules:
 *       - api-key: "key-123"
 *         limit: 10
 *         window-size-seconds: 60
 *         algorithm: TOKEN_BUCKET
 */
@Data
@ConfigurationProperties(prefix = "throttlr")
public class RateLimiterProperties {

    /** If Redis is unreachable: true = allow all traffic, false = block all traffic */
    private boolean failOpen = true;

    /** Fallback rule applied to any API key not listed in rules */
    private RuleConfig defaultRule;

    /** Per-API-key rules — checked before defaultRule */
    private List<RuleConfig> rules = new ArrayList<>();

    @Data
    public static class RuleConfig {
        private String apiKey;
        private int limit = 100;
        private int windowSizeSeconds = 60;
        private AlgorithmType algorithm = AlgorithmType.FIXED_WINDOW;
    }
}
