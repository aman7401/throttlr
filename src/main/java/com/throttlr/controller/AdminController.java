package com.throttlr.controller;

import com.throttlr.config.RateLimiterProperties;
import com.throttlr.storage.StorageBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for ops/debugging — not rate-limited themselves.
 *
 * GET  /admin/rules          → list all configured rules
 * GET  /admin/rules/{apiKey} → get rule for a specific key
 * DELETE /admin/reset/{apiKey} → clear rate limit counters for a key (all algorithms)
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RateLimiterProperties properties;
    private final StorageBackend storageBackend;

    @GetMapping("/rules")
    public ResponseEntity<List<RateLimiterProperties.RuleConfig>> getAllRules() {
        return ResponseEntity.ok(properties.getRules());
    }

    @GetMapping("/rules/{apiKey}")
    public ResponseEntity<?> getRuleForKey(@PathVariable String apiKey) {
        return properties.getRules().stream()
                .filter(r -> apiKey.equals(r.getApiKey()))
                .findFirst()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of(
                        "message", "No specific rule found, default rule applies",
                        "defaultRule", properties.getDefaultRule()
                )));
    }

    @DeleteMapping("/reset/{apiKey}")
    public ResponseEntity<Map<String, String>> resetApiKey(@PathVariable String apiKey) {
        // Clear Redis keys for all algorithm prefixes for this API key
        List.of("throttlr:fw:", "throttlr:swl:", "throttlr:swc:", "throttlr:tb:", "throttlr:lb:")
                .forEach(prefix -> storageBackend.reset(prefix + apiKey));

        return ResponseEntity.ok(Map.of(
                "message", "Rate limit counters cleared for key: " + apiKey
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "throttlr",
                "failOpen", String.valueOf(properties.isFailOpen())
        ));
    }
}
