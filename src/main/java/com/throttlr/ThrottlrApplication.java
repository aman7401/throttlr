package com.throttlr;

import com.throttlr.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimiterProperties.class)
public class ThrottlrApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThrottlrApplication.class, args);
    }
}
