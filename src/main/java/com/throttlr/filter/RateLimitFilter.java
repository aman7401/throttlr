package com.throttlr.filter;

import com.throttlr.model.RateLimitResult;
import com.throttlr.service.ApiKeyService;
import com.throttlr.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip rate limiting for actuator and admin endpoints
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = apiKeyService.extractApiKey(request);

        if (!apiKeyService.isPresent(apiKey)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header", 0);
            return;
        }

        RateLimitResult result = rateLimiterService.checkRateLimit(apiKey);

        // Standard rate limit headers — visible to API consumers
        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingRequests()));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(result.getResetTimeSeconds()));

        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded for key [{}]", apiKey);
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", result.getResetTimeSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, long resetInSeconds)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"error\": \"%s\", \"resetInSeconds\": %d}", message, resetInSeconds
        ));
    }
}
