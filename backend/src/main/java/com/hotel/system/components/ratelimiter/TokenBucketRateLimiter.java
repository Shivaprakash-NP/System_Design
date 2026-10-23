package com.hotel.system.components.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> rateLimitScript;
    private final DefaultRedisScript<Long> queryTokensScript;

    @Value("${ratelimit.capacity:100}")
    private int defaultCapacity;

    @Value("${ratelimit.refill-rate:2}")
    private int defaultRefillRate;

    // Metrics counters
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final AtomicLong totalTokensConsumed = new AtomicLong(0);
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicReference<Double> lastCheckLatencyMs = new AtomicReference<>(0.0);

    // Audit log of recent requests across entire application
    private static final int MAX_AUDIT_LOGS = 30;
    private final ConcurrentLinkedDeque<RateLimitAuditEntry> recentAuditLogs = new ConcurrentLinkedDeque<>();

    // Fallback in-memory buckets in case Redis is unavailable
    private final ConcurrentHashMap<String, InMemoryBucket> localBuckets = new ConcurrentHashMap<>();

    public record TokenConsumptionResult(
            boolean allowed,
            long remainingTokens,
            double latencyMs,
            int capacity,
            int refillRate
    ) {}

    public record RateLimitAuditEntry(
            String id,
            String timestamp,
            String client,
            String method,
            String endpoint,
            String action,
            boolean allowed,
            int statusCode,
            long tokensRemaining,
            double latencyMs
    ) {}

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // Lua script guarantees atomicity of token consumption in Redis and returns [allowed, remainingTokens]
        String luaConsumeScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4] or 1)
            
            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])
            
            if tokens == nil then
                tokens = capacity
                last_refill = now
            else
                local time_passed = math.max(0, now - last_refill)
                local new_tokens = math.floor(time_passed * (refill_rate / 1000))
                if new_tokens > 0 then
                    tokens = math.min(capacity, tokens + new_tokens)
                    last_refill = now
                end
            end
            
            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end
            
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
            redis.call('PEXPIRE', key, 60000) -- Expire key after 60s of inactivity
            return { allowed, tokens }
        """;

        String luaQueryScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])
            
            if tokens == nil then
                return capacity
            else
                local time_passed = math.max(0, now - last_refill)
                local new_tokens = math.floor(time_passed * (refill_rate / 1000))
                return math.min(capacity, tokens + new_tokens)
            end
        """;

        this.rateLimitScript = new DefaultRedisScript<>(luaConsumeScript, List.class);
        this.queryTokensScript = new DefaultRedisScript<>(luaQueryScript, Long.class);
    }

    @Override
    public boolean isAllowed(String key) {
        return consumeToken(key, defaultCapacity, defaultRefillRate, 1).allowed();
    }

    @Override
    public boolean isAllowed(String userId, int capacity, int refillRate) {
        return consumeToken(userId, capacity, refillRate, 1).allowed();
    }

    /**
     * Consumes requested tokens and returns detailed result including remaining tokens and measured latency.
     */
    public TokenConsumptionResult consumeToken(String userId, int capacity, int refillRate, int requested) {
        String key = "ratelimit:" + userId;
        long nowMilli = Instant.now().toEpochMilli();
        long startNanos = System.nanoTime();

        try {
            List<?> result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(nowMilli),
                    String.valueOf(requested)
            );

            long elapsedNanos = System.nanoTime() - startNanos;
            double latencyMs = elapsedNanos / 1_000_000.0;
            totalLatencyNanos.addAndGet(elapsedNanos);
            totalChecks.incrementAndGet();
            lastCheckLatencyMs.set(latencyMs);

            boolean allowed = false;
            long remaining = capacity;

            if (result != null && result.size() >= 2) {
                allowed = ((Number) result.get(0)).longValue() == 1L;
                remaining = ((Number) result.get(1)).longValue();
            }

            if (allowed) {
                allowedRequests.incrementAndGet();
                totalTokensConsumed.addAndGet(requested);
            } else {
                rejectedRequests.incrementAndGet();
            }

            return new TokenConsumptionResult(allowed, remaining, latencyMs, capacity, refillRate);

        } catch (Exception e) {
            logger.warn("Redis unavailable for rate limiter; using local in-memory fallback: {}", e.getMessage());
            long elapsedNanos = System.nanoTime() - startNanos;
            double latencyMs = elapsedNanos / 1_000_000.0;
            totalLatencyNanos.addAndGet(elapsedNanos);
            totalChecks.incrementAndGet();
            lastCheckLatencyMs.set(latencyMs);

            return fallbackLocalConsume(userId, capacity, refillRate, requested, nowMilli, latencyMs);
        }
    }

    public TokenConsumptionResult consumeToken(String userId) {
        return consumeToken(userId, defaultCapacity, defaultRefillRate, 1);
    }

    /**
     * Consumes token and records entry in live audit log stream.
     */
    public TokenConsumptionResult consumeAndRecord(String client, String method, String endpoint, String action, int capacity, int refillRate) {
        TokenConsumptionResult result = consumeToken(client, capacity, refillRate, 1);
        int statusCode = result.allowed() ? 200 : 429;
        recordAudit(client, method, endpoint, action, result.allowed(), statusCode, result.remainingTokens(), result.latencyMs());
        return result;
    }

    public void recordAudit(String client, String method, String endpoint, String action, boolean allowed, int statusCode, long tokensRemaining, double latencyMs) {
        RateLimitAuditEntry entry = new RateLimitAuditEntry(
                UUID.randomUUID().toString().substring(0, 8),
                LocalDateTime.now().format(TIME_FORMATTER),
                client != null ? client : "anonymous",
                method != null ? method : "GET",
                endpoint != null ? endpoint : "/",
                action != null ? action : "API_REQUEST",
                allowed,
                statusCode,
                tokensRemaining,
                latencyMs
        );

        recentAuditLogs.addFirst(entry);
        while (recentAuditLogs.size() > MAX_AUDIT_LOGS) {
            recentAuditLogs.pollLast();
        }
    }

    /**
     * Queries available tokens in Redis without consuming.
     */
    public long getAvailableTokens(String userId, int capacity, int refillRate) {
        String key = "ratelimit:" + userId;
        long nowMilli = Instant.now().toEpochMilli();
        try {
            Long tokens = redisTemplate.execute(
                    queryTokensScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(nowMilli)
            );
            return tokens != null ? tokens : capacity;
        } catch (Exception e) {
            InMemoryBucket bucket = localBuckets.get(userId);
            if (bucket != null) {
                synchronized (bucket) {
                    long passed = Math.max(0, nowMilli - bucket.lastRefill);
                    int newTokens = (int) (passed * refillRate / 1000);
                    return Math.min(capacity, bucket.tokens + newTokens);
                }
            }
            return capacity;
        }
    }

    public long getAvailableTokens(String userId) {
        return getAvailableTokens(userId, defaultCapacity, defaultRefillRate);
    }

    private TokenConsumptionResult fallbackLocalConsume(String userId, int capacity, int refillRate, int requested, long nowMilli, double latencyMs) {
        InMemoryBucket bucket = localBuckets.computeIfAbsent(userId, k -> new InMemoryBucket(capacity, nowMilli));
        synchronized (bucket) {
            long passed = Math.max(0, nowMilli - bucket.lastRefill);
            int newTokens = (int) (passed * refillRate / 1000);
            if (newTokens > 0) {
                bucket.tokens = Math.min(capacity, bucket.tokens + newTokens);
                bucket.lastRefill = nowMilli;
            }

            boolean allowed = bucket.tokens >= requested;
            if (allowed) {
                bucket.tokens -= requested;
                allowedRequests.incrementAndGet();
                totalTokensConsumed.addAndGet(requested);
            } else {
                rejectedRequests.incrementAndGet();
            }

            return new TokenConsumptionResult(allowed, bucket.tokens, latencyMs, capacity, refillRate);
        }
    }

    @Override
    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    @Override
    public long getRejectedRequests() {
        return rejectedRequests.get();
    }

    @Override
    public double getRejectionRate() {
        long total = allowedRequests.get() + rejectedRequests.get();
        if (total == 0) return 0.0;
        return ((double) rejectedRequests.get() / total) * 100.0;
    }

    public long getTotalTokensConsumed() {
        return totalTokensConsumed.get();
    }

    public double getAverageLatencyMs() {
        long checks = totalChecks.get();
        if (checks == 0) return 0.0;
        return (totalLatencyNanos.get() / (double) checks) / 1_000_000.0;
    }

    public double getLastCheckLatencyMs() {
        return lastCheckLatencyMs.get();
    }

    public List<RateLimitAuditEntry> getRecentAuditLogs() {
        return new ArrayList<>(recentAuditLogs);
    }

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public int getDefaultRefillRate() {
        return defaultRefillRate;
    }

    @Override
    public void resetMetrics() {
        allowedRequests.set(0);
        rejectedRequests.set(0);
        totalTokensConsumed.set(0);
        totalChecks.set(0);
        totalLatencyNanos.set(0);
        lastCheckLatencyMs.set(0.0);
        recentAuditLogs.clear();
        localBuckets.clear();
    }

    private static class InMemoryBucket {
        int tokens;
        long lastRefill;

        InMemoryBucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }
    }
}
