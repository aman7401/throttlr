package com.throttlr.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    private static final String API_KEY_HEADER = "X-API-Key";

    public String extractApiKey(HttpServletRequest request) {
        return request.getHeader(API_KEY_HEADER);
    }

    public boolean isPresent(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }
}
