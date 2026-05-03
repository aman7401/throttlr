package com.throttlr.controller;

import com.throttlr.config.RateLimiterProperties;
import com.throttlr.config.RateLimiterProperties.RuleConfig;
import com.throttlr.service.RuleStore;
import com.throttlr.storage.StorageBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Admin endpoints for managing rate limit rules at runtime.
 * Rule priority: Redis (dynamic) > YAML (static) > default rule.
 *
 * GET    /admin/rules              → all rules (Redis + YAML merged)
 * GET    /admin/rules/{apiKey}     → rule for one key
 * POST   /admin/rules              → create a new rule in Redis
 * PUT    /admin/rules/{apiKey}     → update an existing Redis rule
 * DELETE /admin/rules/{apiKey}     → remove Redis rule (YAML rule takes over if exists)
 * DELETE /admin/reset/{apiKey}     → clear rate limit counters for a key
 * GET    /admin/health             → service health
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RateLimiterProperties properties;
    private final RuleStore ruleStore;
    private final StorageBackend storageBackend;

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getAllRules() {
        List<RuleConfig> redisRules = ruleStore.findAll();

        // YAML rules that are NOT overridden in Redis
        List<RuleConfig> yamlOnlyRules = properties.getRules().stream()
                .filter(r -> redisRules.stream().noneMatch(rr -> rr.getApiKey().equals(r.getApiKey())))
                .toList();

        return ResponseEntity.ok(Map.of(
                "dynamic", redisRules,
                "static", yamlOnlyRules,
                "defaultRule", properties.getDefaultRule() != null ? properties.getDefaultRule() : "not configured"
        ));
    }

    @GetMapping("/rules/{apiKey}")
    public ResponseEntity<?> getRuleForKey(@PathVariable String apiKey) {
        // Redis first, then YAML
        return ruleStore.findByApiKey(apiKey)
                .<ResponseEntity<?>>map(rule -> ResponseEntity.ok(Map.of("rule", rule, "source", "redis")))
                .orElseGet(() -> properties.getRules().stream()
                        .filter(r -> apiKey.equals(r.getApiKey()))
                        .findFirst()
                        .<ResponseEntity<?>>map(rule -> ResponseEntity.ok(Map.of("rule", rule, "source", "yaml")))
                        .orElse(ResponseEntity.ok(Map.of(
                                "message", "No specific rule found, default rule applies",
                                "defaultRule", properties.getDefaultRule()
                        ))));
    }

    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@RequestBody RuleConfig rule) {
        if (rule.getApiKey() == null || rule.getApiKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiKey is required"));
        }
        if (ruleStore.exists(rule.getApiKey())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Rule already exists for key: " + rule.getApiKey()
                            + ". Use PUT to update."));
        }
        ruleStore.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Rule created", "rule", rule));
    }

    @PutMapping("/rules/{apiKey}")
    public ResponseEntity<?> updateRule(@PathVariable String apiKey, @RequestBody RuleConfig rule) {
        rule.setApiKey(apiKey);
        ruleStore.save(rule);
        return ResponseEntity.ok(Map.of("message", "Rule updated", "rule", rule));
    }

    @DeleteMapping("/rules/{apiKey}")
    public ResponseEntity<Map<String, String>> deleteRule(@PathVariable String apiKey) {
        boolean removed = ruleStore.delete(apiKey);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No dynamic rule found for key: " + apiKey));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Dynamic rule removed for key: " + apiKey + ". YAML rule applies if configured."
        ));
    }

    @DeleteMapping("/reset/{apiKey}")
    public ResponseEntity<Map<String, String>> resetCounters(@PathVariable String apiKey) {
        List.of("throttlr:fw:", "throttlr:swl:", "throttlr:swc:", "throttlr:tb:", "throttlr:lb:")
                .forEach(prefix -> storageBackend.reset(prefix + apiKey));
        return ResponseEntity.ok(Map.of("message", "Rate limit counters cleared for key: " + apiKey));
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
