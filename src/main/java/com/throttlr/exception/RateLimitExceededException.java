package com.throttlr.exception;

import com.throttlr.model.RateLimitResult;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitResult result;

    public RateLimitExceededException(RateLimitResult result) {
        super("Rate limit exceeded");
        this.result = result;
    }
}
