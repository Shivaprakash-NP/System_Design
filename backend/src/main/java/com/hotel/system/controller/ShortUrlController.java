package com.hotel.system.controller;

import com.hotel.system.components.urlshortener.UrlShortenerService;
import com.hotel.system.model.ShortUrl;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/short-url")
@RequiredArgsConstructor
public class ShortUrlController {

    private final UrlShortenerService urlShortenerService;

    @Data
    public static class CreateShortUrlRequest {
        private String originalUrl;
        private String customCode;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createShortUrl(
            @RequestBody(required = false) CreateShortUrlRequest body,
            @RequestParam(required = false) String originalUrl,
            @RequestParam(required = false) String customCode,
            Authentication authentication
    ) {
        String targetUrl = body != null && body.getOriginalUrl() != null 
                ? body.getOriginalUrl() 
                : originalUrl;
        String slug = body != null && body.getCustomCode() != null 
                ? body.getCustomCode() 
                : customCode;

        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The destination 'originalUrl' is required."));
        }

        String user = authentication != null ? authentication.getName() : "anonymous";
        try {
            ShortUrl shortUrl = urlShortenerService.createShortUrl(targetUrl, slug, user);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("shortCode", shortUrl.getShortCode());
            response.put("shortUrl", "http://localhost:8080/r/" + shortUrl.getShortCode());
            response.put("redirectPath", "/r/" + shortUrl.getShortCode());
            response.put("originalUrl", shortUrl.getOriginalUrl());
            response.put("clickCount", shortUrl.getClickCount() != null ? shortUrl.getClickCount() : 0);
            response.put("createdAt", shortUrl.getCreatedAt());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate short URL: " + e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ShortUrl>> getRecentShortUrls() {
        return ResponseEntity.ok(urlShortenerService.getRecentShortUrls());
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> getShortUrlDetails(@PathVariable String shortCode) {
        return urlShortenerService.getShortUrlDetails(shortCode)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Short URL not found for code: " + shortCode)));
    }
}
