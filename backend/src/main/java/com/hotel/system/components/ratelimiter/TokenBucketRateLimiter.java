package com.hotel.system.components.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    @Value("${ratelimit.capacity:100}")
    private int defaultCapacity;

    @Value("${ratelimit.refill-rate:2}")
    private int defaultRefillRate;

    // Metrics counters
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    // Fallback in-memory buckets in case Redis is unavailable
    private final ConcurrentHashMap<String, InMemoryBucket> localBuckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // Lua script guarantees atomicity of the token bucket logic in Redis
        String luaScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = 1
            
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
            
            if tokens >= requested then
                tokens = tokens - requested
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                redis.call('PEXPIRE', key, 60000) -- Expire key after 60s
                return 1
            else
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                return 0
            end
        """;

        this.rateLimitScript = new DefaultRedisScript<>(luaScript, Long.class);
    }

    @Override
    public boolean isAllowed(String key) {
        return isAllowed(key, defaultCapacity, defaultRefillRate);
    }

    @Override
    public boolean isAllowed(String userId, int capacity, int refillRate) {
        String key = "ratelimit:" + userId;
        long nowMilli = Instant.now().toEpochMilli();

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(nowMilli)
            );

            boolean allowed = result != null && result == 1L;
            if (allowed) {
                allowedRequests.incrementAndGet();
            } else {
                rejectedRequests.incrementAndGet();
            }
            return allowed;

        } catch (Exception e) {
            logger.warn("Redis unavailable for rate limiter; using local in-memory fallback bucket: {}", e.getMessage());
            boolean allowed = fallbackLocalIsAllowed(userId, capacity, refillRate, nowMilli);
            if (allowed) {
                allowedRequests.incrementAndGet();
            } else {
                rejectedRequests.incrementAndGet();
            }
            return allowed;
        }
    }

    private boolean fallbackLocalIsAllowed(String userId, int capacity, int refillRate, long nowMilli) {
        InMemoryBucket bucket = localBuckets.computeIfAbsent(userId, k -> new InMemoryBucket(capacity, nowMilli));
        synchronized (bucket) {
            long passed = Math.max(0, nowMilli - bucket.lastRefill);
            int newTokens = (int) (passed * refillRate / 1000);
            if (newTokens > 0) {
                bucket.tokens = Math.min(capacity, bucket.tokens + newTokens);
                bucket.lastRefill = nowMilli;
            }
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return true;
            }
            return false;
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

    @Override
    public void resetMetrics() {
        allowedRequests.set(0);
        rejectedRequests.set(0);
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
