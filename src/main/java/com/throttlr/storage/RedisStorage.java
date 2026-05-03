package com.throttlr.storage;

import com.throttlr.model.AlgorithmType;
import com.throttlr.model.RateLimitResult;
import com.throttlr.model.RateLimitRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStorage implements StorageBackend {

    private final StringRedisTemplate redisTemplate;

    // One Lua script per algorithm, loaded once at startup
    private final Map<AlgorithmType, DefaultRedisScript<List>> scripts = new EnumMap<>(AlgorithmType.class);

    @PostConstruct
    public void loadScripts() {
        for (AlgorithmType type : AlgorithmType.values()) {
            // Naming convention: FIXED_WINDOW -> lua/fixed_window.lua
            String path = "lua/" + type.name().toLowerCase() + ".lua";
            DefaultRedisScript<List> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
            script.setResultType(List.class);
            scripts.put(type, script);
            log.info("Loaded Lua script: {}", path);
        }
    }

    /**
     * Sends the correct Lua script to Redis.
     * Redis runs it atomically — check + increment happen as one indivisible operation.
     *
     * KEYS[1] = the Redis key (built by the algorithm class)
     * ARGV[1] = limit
     * ARGV[2] = windowSizeSeconds
     * ARGV[3] = current timestamp in milliseconds
     *
     * All scripts return a 3-element list: { allowed(1/0), remaining, resetTimeSeconds }
     */
    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult checkAndIncrement(String key, RateLimitRule rule, Instant now) {
        DefaultRedisScript<List> script = scripts.get(rule.getAlgorithm());

        List<Long> result = (List<Long>) redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(rule.getLimit()),
                String.valueOf(rule.getWindowSizeSeconds()),
                String.valueOf(now.toEpochMilli())
        );

        boolean allowed = result.get(0) == 1L;
        int remaining   = result.get(1).intValue();
        long resetTime  = result.get(2);

        return allowed
                ? RateLimitResult.allowed(rule.getLimit(), remaining, resetTime)
                : RateLimitResult.denied(rule.getLimit(), resetTime);
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete(key);
    }
}
