package com.throttlr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class RateLimitRequest {
    private String apiKey;
    private String endpoint;
    private Instant timestamp;
}
