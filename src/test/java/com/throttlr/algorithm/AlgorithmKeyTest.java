package com.throttlr.algorithm;

import com.throttlr.model.AlgorithmType;
import com.throttlr.model.RateLimitRequest;
import com.throttlr.model.RateLimitResult;
import com.throttlr.model.RateLimitRule;
import com.throttlr.storage.StorageBackend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * Unit tests for algorithm key-building logic.
 * StorageBackend is mocked — no Redis needed.
 */
@ExtendWith(MockitoExtension.class)
class AlgorithmKeyTest {

    @Mock
    private StorageBackend storageBackend;

    private RateLimitResult allowedResult;

    @BeforeEach
    void setUp() {
        allowedResult = RateLimitResult.allowed(10, 9, 60);
        when(storageBackend.checkAndIncrement(any(), any(), any())).thenReturn(allowedResult);
    }

    // --- Fixed Window ---

    @Test
    @DisplayName("FixedWindow builds key with correct windowId")
    void fixedWindow_buildsKeyWithWindowId() {
        Instant now = Instant.ofEpochSecond(3660); // window = 3660/60 = 61
        RateLimitRule rule = rule(AlgorithmType.FIXED_WINDOW, 60);

        new FixedWindowAlgorithm(storageBackend).isAllowed(request("key-fw", now), rule);

        assertKey("throttlr:fw:key-fw:61");
    }

    @Test
    @DisplayName("FixedWindow uses different windowId for different time buckets")
    void fixedWindow_differentWindowIds_forDifferentTimeBuckets() {
        RateLimitRule rule = rule(AlgorithmType.FIXED_WINDOW, 60);
        FixedWindowAlgorithm algo = new FixedWindowAlgorithm(storageBackend);

        algo.isAllowed(request("key-fw", Instant.ofEpochSecond(3600)), rule);
        assertKey("throttlr:fw:key-fw:60");

        clearInvocations(storageBackend);

        algo.isAllowed(request("key-fw", Instant.ofEpochSecond(3660)), rule);
        assertKey("throttlr:fw:key-fw:61");
    }

    // --- Sliding Window Log ---

    @Test
    @DisplayName("SlidingWindowLog builds key without windowId")
    void slidingWindowLog_buildsKeyWithoutWindowId() {
        new SlidingWindowLogAlgorithm(storageBackend)
                .isAllowed(request("key-swl", Instant.now()), rule(AlgorithmType.SLIDING_WINDOW_LOG, 60));

        assertKey("throttlr:swl:key-swl");
    }

    // --- Sliding Window Counter ---

    @Test
    @DisplayName("SlidingWindowCounter builds base key without windowId")
    void slidingWindowCounter_buildsBaseKey() {
        new SlidingWindowCounterAlgorithm(storageBackend)
                .isAllowed(request("key-swc", Instant.now()), rule(AlgorithmType.SLIDING_WINDOW_COUNTER, 60));

        assertKey("throttlr:swc:key-swc");
    }

    // --- Token Bucket ---

    @Test
    @DisplayName("TokenBucket builds key with tb prefix")
    void tokenBucket_buildsKeyWithTbPrefix() {
        new TokenBucketAlgorithm(storageBackend)
                .isAllowed(request("key-tb", Instant.now()), rule(AlgorithmType.TOKEN_BUCKET, 60));

        assertKey("throttlr:tb:key-tb");
    }

    // --- Leaky Bucket ---

    @Test
    @DisplayName("LeakyBucket builds key with lb prefix")
    void leakyBucket_buildsKeyWithLbPrefix() {
        new LeakyBucketAlgorithm(storageBackend)
                .isAllowed(request("key-lb", Instant.now()), rule(AlgorithmType.LEAKY_BUCKET, 60));

        assertKey("throttlr:lb:key-lb");
    }

    // --- Result passthrough ---

    @Test
    @DisplayName("All algorithms pass through StorageBackend result unchanged")
    void allAlgorithms_passThroughStorageResult() {
        RateLimitResult denied = RateLimitResult.denied(10, 30);
        when(storageBackend.checkAndIncrement(any(), any(), any())).thenReturn(denied);

        RateLimitResult result = new FixedWindowAlgorithm(storageBackend)
                .isAllowed(request("key-x", Instant.now()), rule(AlgorithmType.FIXED_WINDOW, 60));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getResetTimeSeconds()).isEqualTo(30);
    }

    // --- Helpers ---

    private void assertKey(String expectedKey) {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageBackend).checkAndIncrement(keyCaptor.capture(), any(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
    }

    private RateLimitRequest request(String apiKey, Instant timestamp) {
        return RateLimitRequest.builder().apiKey(apiKey).timestamp(timestamp).build();
    }

    private RateLimitRule rule(AlgorithmType algorithm, int windowSizeSeconds) {
        return RateLimitRule.builder()
                .apiKey("test")
                .limit(10)
                .windowSizeSeconds(windowSizeSeconds)
                .algorithm(algorithm)
                .build();
    }
}
