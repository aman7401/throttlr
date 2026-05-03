package com.throttlr.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sample endpoints to test the rate limiter end-to-end.
 * Every request here must include X-API-Key header — RateLimitFilter handles it.
 *
 * Test with:
 *   curl -H "X-API-Key: key-basic" http://localhost:8080/api/hello
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok(Map.of(
                "message", "Hello! Request allowed.",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getData() {
        return ResponseEntity.ok(Map.of(
                "items", List.of("item-1", "item-2", "item-3"),
                "timestamp", Instant.now().toString()
        ));
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> process(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "received", body.size() + " fields"
        ));
    }
}
