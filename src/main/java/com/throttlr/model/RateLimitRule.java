package com.throttlr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {
    private String apiKey;
    private int limit;
    private int windowSizeSeconds;
    private AlgorithmType algorithm;
}
