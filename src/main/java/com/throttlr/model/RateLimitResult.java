package com.throttlr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private int limit;
    private int remainingRequests;
    private long resetTimeSeconds;
    private String message;

    public static RateLimitResult allowed(int limit, int remaining, long resetTime) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remainingRequests(remaining)
                .resetTimeSeconds(resetTime)
                .message("Request allowed")
                .build();
    }

    public static RateLimitResult denied(int limit, long resetTime) {
        return RateLimitResult.builder()
                .allowed(false)
                .limit(limit)
                .remainingRequests(0)
                .resetTimeSeconds(resetTime)
                .message("Rate limit exceeded")
                .build();
    }
}
