package com.hotel.system.controller;

import com.hotel.system.components.urlshortener.UrlShortenerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    @GetMapping("/r/{shortCode}")
    public ResponseEntity<?> redirectToTarget(@PathVariable String shortCode) {
        Optional<String> targetUrlOpt = urlShortenerService.resolve(shortCode);

        if (targetUrlOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Short URL not found or expired: " + shortCode);
        }

        String target = targetUrlOpt.get();
        if (target.startsWith("/")) {
            // Frontend route, redirect to local frontend app
            target = "http://localhost:5173" + target;
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
