package com.throttlr.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.throttlr.config.RateLimiterProperties.RuleConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists rate limit rules in Redis so that admin changes survive app restarts.
 *
 * Storage: single Redis hash at key "throttlr:config:rules"
 *   field = apiKey  (e.g. "key-abc")
 *   value = JSON    (e.g. {"apiKey":"key-abc","limit":50,"windowSizeSeconds":60,"algorithm":"TOKEN_BUCKET"})
 *
 * This avoids KEYS * scans and keeps all rules in one atomic structure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleStore {

    private static final String RULES_HASH_KEY = "throttlr:config:rules";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<RuleConfig> findByApiKey(String apiKey) {
        Object json = redisTemplate.opsForHash().get(RULES_HASH_KEY, apiKey);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json.toString(), RuleConfig.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize rule for key [{}]: {}", apiKey, e.getMessage());
            return Optional.empty();
        }
    }

    public List<RuleConfig> findAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RULES_HASH_KEY);
        return entries.values().stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json.toString(), RuleConfig.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize rule: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();
    }

    public void save(RuleConfig rule) {
        try {
            String json = objectMapper.writeValueAsString(rule);
            redisTemplate.opsForHash().put(RULES_HASH_KEY, rule.getApiKey(), json);
            log.info("Saved rule for key [{}] to Redis", rule.getApiKey());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule for key: " + rule.getApiKey(), e);
        }
    }

    public boolean delete(String apiKey) {
        Long removed = redisTemplate.opsForHash().delete(RULES_HASH_KEY, apiKey);
        return removed != null && removed > 0;
    }

    public boolean exists(String apiKey) {
        return redisTemplate.opsForHash().hasKey(RULES_HASH_KEY, apiKey);
    }
}
