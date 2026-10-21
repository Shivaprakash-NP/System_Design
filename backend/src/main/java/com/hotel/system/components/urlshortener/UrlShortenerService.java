package com.hotel.system.components.urlshortener;

import com.hotel.system.model.ShortUrl;
import com.hotel.system.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private static final String BASE62_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = BASE62_ALPHABET.length();
    private static final String PREFIX = "htl_";

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
        validateDestinationUrl(originalUrl);

        String code = customCode != null && !customCode.trim().isEmpty() 
                ? customCode.trim() 
                : encode(System.currentTimeMillis() % 1000000000L);

        // Check if exists
        Optional<ShortUrl> existing = shortUrlRepository.findByShortCode(code);
        if (existing.isPresent()) {
            return existing.get();
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(code)
                .originalUrl(originalUrl)
                .clickCount(0L)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();

        ShortUrl saved = shortUrlRepository.save(shortUrl);
        // Cache in Redis for high-speed resolution
        try {
            redisTemplate.opsForValue().set("shorturl:" + code, originalUrl, Duration.ofDays(7));
        } catch (Exception ignored) {}

        return saved;
    }

    public Optional<String> resolve(String shortCode) {
        long start = System.nanoTime();
        try {
            // Check Redis cache first
            try {
                String cached = redisTemplate.opsForValue().get("shorturl:" + shortCode);
                if (cached != null) {
                    redirectionCount.incrementAndGet();
                    totalRedirectLatencyNanos.addAndGet(System.nanoTime() - start);
                    return Optional.of(cached);
                }
            } catch (Exception ignored) {}

            Optional<ShortUrl> entity = shortUrlRepository.findByShortCode(shortCode);
            if (entity.isPresent()) {
                ShortUrl s = entity.get();
                s.setClickCount(s.getClickCount() + 1);
                s.setLastAccessedAt(LocalDateTime.now());
                shortUrlRepository.save(s);

                try {
                    redisTemplate.opsForValue().set("shorturl:" + shortCode, s.getOriginalUrl(), Duration.ofDays(7));
                } catch (Exception ignored) {}

                redirectionCount.incrementAndGet();
                totalRedirectLatencyNanos.addAndGet(System.nanoTime() - start);
                return Optional.of(s.getOriginalUrl());
            }

            return Optional.empty();
        } finally {
            // Metrics tracked
        }
    }

    private void validateDestinationUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination URL cannot be empty");
        }
        // Protect against open redirects to malicious schemes
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("/")) {
            throw new IllegalArgumentException("Invalid URL protocol. Only http, https, or relative paths allowed.");
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
