package com.hotel.system.components.urlshortener;

import com.hotel.system.model.ShortUrl;
import com.hotel.system.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private static final String BASE62_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_ALPHABET.length();

    private final ShortUrlRepository shortUrlRepository;
    private final StringRedisTemplate redisTemplate;

    private final AtomicLong redirectionCount = new AtomicLong(0);
    private final AtomicLong totalRedirectLatencyNanos = new AtomicLong(0);

    /**
     * Encodes a unique numeric ID into a Base62 string.
     */
    public String encode(long id) {
        if (id == 0) {
            return BASE62_ALPHABET.substring(0, 1);
        }
        StringBuilder sb = new StringBuilder();
        long current = Math.abs(id);
        while (current > 0) {
            int remainder = (int) (current % BASE);
            sb.append(BASE62_ALPHABET.charAt(remainder));
            current /= BASE;
        }
        return sb.reverse().toString();
    }

    public long decode(String shortCode) {
        long id = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            char ch = shortCode.charAt(i);
            int idx = BASE62_ALPHABET.indexOf(ch);
            if (idx == -1) {
                return 0;
            }
            id = id * BASE + idx;
        }
        return id;
    }

    public ShortUrl createShortUrl(String originalUrl, String customCode, String createdBy) {
        String cleanUrl = validateDestinationUrl(originalUrl);

        // 1. Check if the exact same originalUrl has already been shortened without a conflicting custom code
        if (customCode == null || customCode.trim().isEmpty()) {
            Optional<ShortUrl> existingOriginal = shortUrlRepository.findByOriginalUrl(cleanUrl);
            if (existingOriginal.isPresent()) {
                ShortUrl existing = existingOriginal.get();
                // Ensure cached in Redis
                try {
                    redisTemplate.opsForValue().set("shorturl:" + existing.getShortCode(), cleanUrl, Duration.ofDays(7));
                } catch (Exception ignored) {}
                return existing;
            }
        }

        // 2. Determine or generate unique short code
        String code;
        if (customCode != null && !customCode.trim().isEmpty()) {
            code = customCode.trim();
            if (!code.matches("^[a-zA-Z0-9_-]{3,30}$")) {
                throw new IllegalArgumentException("Custom short code must be 3-30 characters alphanumeric (letters, digits, '-' or '_').");
            }
            Optional<ShortUrl> conflict = shortUrlRepository.findByShortCode(code);
            if (conflict.isPresent()) {
                if (conflict.get().getOriginalUrl().equals(cleanUrl)) {
                    return conflict.get();
                }
                throw new IllegalArgumentException("Custom code '" + code + "' is already in use for another URL. Please choose another code.");
            }
        } else {
            // Collision-resistant Base62 generation
            int attempts = 0;
            do {
                long token = (System.currentTimeMillis() % 100_000_000_000L) * 1000L + ThreadLocalRandom.current().nextInt(1000);
                code = encode(token);
                attempts++;
            } while (shortUrlRepository.findByShortCode(code).isPresent() && attempts < 10);

            if (shortUrlRepository.findByShortCode(code).isPresent()) {
                code = code + encode(System.nanoTime() % 10000L);
            }
        }

        // 3. Persist mapping
        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(code)
                .originalUrl(cleanUrl)
                .clickCount(0L)
                .createdBy(createdBy != null ? createdBy : "system")
                .createdAt(LocalDateTime.now())
                .build();

        ShortUrl saved = shortUrlRepository.save(shortUrl);

        // 4. Cache in Redis for high-speed sub-millisecond resolution
        try {
            redisTemplate.opsForValue().set("shorturl:" + code, cleanUrl, Duration.ofDays(7));
        } catch (Exception ignored) {}

        return saved;
    }

    public Optional<String> resolve(String shortCode) {
        if (shortCode == null || shortCode.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanCode = shortCode.trim();
        long start = System.nanoTime();
        try {
            // 1. Check Redis cache first
            try {
                String cached = redisTemplate.opsForValue().get("shorturl:" + cleanCode);
                if (cached != null) {
                    redirectionCount.incrementAndGet();
                    totalRedirectLatencyNanos.addAndGet(System.nanoTime() - start);

                    // Async/Background click increment in DB
                    shortUrlRepository.findByShortCode(cleanCode).ifPresent(s -> {
                        s.setClickCount((s.getClickCount() != null ? s.getClickCount() : 0L) + 1);
                        s.setLastAccessedAt(LocalDateTime.now());
                        shortUrlRepository.save(s);
                    });

                    return Optional.of(cached);
                }
            } catch (Exception ignored) {}

            // 2. Fallback to MongoDB
            Optional<ShortUrl> entity = shortUrlRepository.findByShortCode(cleanCode);
            if (entity.isPresent()) {
                ShortUrl s = entity.get();
                s.setClickCount((s.getClickCount() != null ? s.getClickCount() : 0L) + 1);
                s.setLastAccessedAt(LocalDateTime.now());
                shortUrlRepository.save(s);

                try {
                    redisTemplate.opsForValue().set("shorturl:" + cleanCode, s.getOriginalUrl(), Duration.ofDays(7));
                } catch (Exception ignored) {}

                redirectionCount.incrementAndGet();
                totalRedirectLatencyNanos.addAndGet(System.nanoTime() - start);
                return Optional.of(s.getOriginalUrl());
            }

            return Optional.empty();
        } finally {
            // Completed
        }
    }

    public List<ShortUrl> getRecentShortUrls() {
        return shortUrlRepository.findTop15ByOrderByCreatedAtDesc();
    }

    public Optional<ShortUrl> getShortUrlDetails(String shortCode) {
        return shortUrlRepository.findByShortCode(shortCode);
    }

    private String validateDestinationUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination URL cannot be empty.");
        }
        String trimmed = url.trim();

        // Relative internal route
        if (trimmed.startsWith("/")) {
            return trimmed;
        }

        // Validate scheme
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Invalid URL protocol. Only HTTP, HTTPS, or relative paths are permitted.");
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid URL: Hostname cannot be empty.");
            }
            return trimmed;
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL format: " + e.getMessage());
        }
    }

    public double getAverageRedirectionLatencyMs() {
        long count = redirectionCount.get();
        if (count == 0) return 0.0;
        return (totalRedirectLatencyNanos.get() / (double) count) / 1_000_000.0;
    }

    public long getRedirectionCount() {
        return redirectionCount.get();
    }
}
