package com.throttlr.integration;

import com.throttlr.model.RateLimitResult;
import com.throttlr.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using a real Redis instance via Testcontainers.
 * Tests the full flow: RateLimiterService → Algorithm → Lua script → Redis.
 */
@SpringBootTest
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void flushRedis() {
        // Clean slate before each test so limits don't bleed between tests
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // --- Fixed Window ---

    @Test
    @DisplayName("Fixed Window: allows requests up to limit then denies")
    void fixedWindow_allowsUpToLimit_thenDenies() {
        for (int i = 0; i < 10; i++) {
            RateLimitResult result = rateLimiterService.checkRateLimit("key-basic");
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingRequests()).isEqualTo(10 - i - 1);
        }
        RateLimitResult denied = rateLimiterService.checkRateLimit("key-basic");
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getRemainingRequests()).isZero();
    }

    // --- Sliding Window Log ---

    @Test
    @DisplayName("Sliding Window Log: allows requests up to limit then denies")
    void slidingWindowLog_allowsUpToLimit_thenDenies() {
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiterService.checkRateLimit("key-sliding-log").isAllowed()).isTrue();
        }
        assertThat(rateLimiterService.checkRateLimit("key-sliding-log").isAllowed()).isFalse();
    }

    // --- Token Bucket ---

    @Test
    @DisplayName("Token Bucket: allows burst up to capacity")
    void tokenBucket_allowsBurstUpToCapacity() {
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiterService.checkRateLimit("key-token").isAllowed()).isTrue();
        }
        assertThat(rateLimiterService.checkRateLimit("key-token").isAllowed()).isFalse();
    }

    // --- Leaky Bucket ---

    @Test
    @DisplayName("Leaky Bucket: allows requests up to capacity then denies")
    void leakyBucket_allowsUpToCapacity_thenDenies() {
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiterService.checkRateLimit("key-leaky").isAllowed()).isTrue();
        }
        assertThat(rateLimiterService.checkRateLimit("key-leaky").isAllowed()).isFalse();
    }

    // --- Independent limits ---

    @Test
    @DisplayName("Different API keys have independent rate limits")
    void differentApiKeys_haveIndependentLimits() {
        // Exhaust key-basic
        for (int i = 0; i < 10; i++) {
            rateLimiterService.checkRateLimit("key-basic");
        }
        assertThat(rateLimiterService.checkRateLimit("key-basic").isAllowed()).isFalse();

        // key-token should be unaffected
        assertThat(rateLimiterService.checkRateLimit("key-token").isAllowed()).isTrue();
    }

    // --- Default rule ---

    @Test
    @DisplayName("Unknown API key falls back to default rule")
    void unknownApiKey_usesDefaultRule() {
        RateLimitResult result = rateLimiterService.checkRateLimit("unknown-key-xyz");
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getLimit()).isEqualTo(100);
    }

    // --- Result fields ---

    @Test
    @DisplayName("Allowed result contains correct limit and remaining fields")
    void allowedResult_hasCorrectFields() {
        RateLimitResult result = rateLimiterService.checkRateLimit("key-basic");
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getLimit()).isEqualTo(10);
        assertThat(result.getRemainingRequests()).isEqualTo(9);
        assertThat(result.getResetTimeSeconds()).isPositive();
    }
}
