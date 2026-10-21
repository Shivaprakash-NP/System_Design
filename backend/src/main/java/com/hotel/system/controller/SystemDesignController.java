package com.hotel.system.controller;

import com.hotel.system.components.autocomplete.AutocompleteService;
import com.hotel.system.components.crawler.ExternalHotelCrawler;
import com.hotel.system.components.idgenerator.SnowflakeIdGenerator;
import com.hotel.system.components.ratelimiter.TokenBucketRateLimiter;
import com.hotel.system.components.router.ConsistentHashRing;
import com.hotel.system.components.urlshortener.UrlShortenerService;
import com.hotel.system.service.RoomService;
import com.hotel.system.service.ShardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/system-design")
@RequiredArgsConstructor
public class SystemDesignController {

    private final TokenBucketRateLimiter rateLimiter;
    private final ShardingService shardingService;
    private final ConsistentHashRing consistentHashRing;
    private final RoomService roomService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final UrlShortenerService urlShortenerService;
    private final ExternalHotelCrawler externalHotelCrawler;
    private final AutocompleteService autocompleteService;

    // 1. Rate Limiter Stats
    @GetMapping("/rate-limiter/stats")
    public ResponseEntity<Map<String, Object>> getRateLimiterStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("allowedRequests", rateLimiter.getAllowedRequests());
        stats.put("rejectedRequests", rateLimiter.getRejectedRequests());
        stats.put("rejectionRatePercentage", String.format("%.2f%%", rateLimiter.getRejectionRate()));
        stats.put("algorithm", "Token Bucket (Redis Lua with In-memory Fallback)");
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/rate-limiter/reset")
    public ResponseEntity<Void> resetRateLimiter() {
        rateLimiter.resetMetrics();
        return ResponseEntity.ok().build();
    }

    // 2. Sharding Statistics
    @GetMapping("/sharding/stats")
    public ResponseEntity<Map<String, Object>> getShardingStats() {
        return ResponseEntity.ok(shardingService.getShardDistributionStatistics());
    }

    @PostMapping("/sharding/add-shard")
    public ResponseEntity<Map<String, Object>> addShard(@RequestParam(defaultValue = "shard_2") String shardName) {
        return ResponseEntity.ok(shardingService.addShard(shardName));
    }

    @PostMapping("/sharding/remove-shard")
    public ResponseEntity<Map<String, Object>> removeShard(@RequestParam(defaultValue = "shard_2") String shardName) {
        return ResponseEntity.ok(shardingService.removeShard(shardName));
    }

    // 3. Consistent Hashing Evaluation
    @GetMapping("/consistent-hashing/stats")
    public ResponseEntity<Map<String, Object>> getConsistentHashingStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("physicalNodes", consistentHashRing.getPhysicalNodes());
        stats.put("virtualNodesCount", consistentHashRing.getRingSize());
        stats.put("virtualNodesPerPhysical", 100);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/consistent-hashing/test-remapping")
    public ResponseEntity<Map<String, Object>> testRemapping(
            @RequestParam(defaultValue = "node_delta") String newNode,
            @RequestParam(defaultValue = "100") int sampleKeysCount) {
        
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < sampleKeysCount; i++) {
            keys.add("hotel_key_" + i);
        }
        return ResponseEntity.ok(consistentHashRing.measureRemapping(keys, newNode));
    }

    // 4. Key-Value Store / Cache Stats
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        long hits = roomService.getCacheHits();
        long misses = roomService.getCacheMisses();
        long total = hits + misses;
        double hitRatio = total > 0 ? (hits * 100.0 / total) : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cacheHits", hits);
        stats.put("cacheMisses", misses);
        stats.put("totalLookups", total);
        stats.put("hitRatioPercentage", String.format("%.2f%%", hitRatio));
        stats.put("averageLookupLatencyMs", String.format("%.3f ms", roomService.getAverageLookupLatencyMs()));
        stats.put("cacheBackend", "Redis 7.0 (with MongoDB fallback)");
        return ResponseEntity.ok(stats);
    }

    // 5. Unique ID Generator Collision & Performance Test
    @PostMapping("/id-generator/benchmark")
    public ResponseEntity<Map<String, Object>> runIdBenchmark(
            @RequestParam(defaultValue = "50000") int totalIds,
            @RequestParam(defaultValue = "10") int threads) throws InterruptedException {

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet(totalIds);
        AtomicInteger collisions = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        int idsPerThread = totalIds / threads;
        long startTime = System.nanoTime();

        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        long id = snowflakeIdGenerator.generateId();
                        if (!generatedIds.add(id)) {
                            collisions.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long durationNanos = System.nanoTime() - startTime;
        double durationMs = durationNanos / 1_000_000.0;
        double throughputIdsPerSec = (totalIds / (durationMs / 1000.0));

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("algorithm", "Twitter Snowflake 64-bit ID");
        results.put("totalIdsRequested", totalIds);
        results.put("uniqueIdsGenerated", generatedIds.size());
        results.put("collisionCount", collisions.get());
        results.put("threads", threads);
        results.put("durationMs", String.format("%.2f ms", durationMs));
        results.put("generationRatePerSecond", String.format("%.0f IDs/sec", throughputIdsPerSec));
        return ResponseEntity.ok(results);
    }

    // 6. URL Shortener Stats
    @GetMapping("/url-shortener/stats")
    public ResponseEntity<Map<String, Object>> getUrlShortenerStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("redirectionCount", urlShortenerService.getRedirectionCount());
        stats.put("averageRedirectionLatencyMs", String.format("%.3f ms", urlShortenerService.getAverageRedirectionLatencyMs()));
        stats.put("encoding", "Base62 with Redis Cache & MongoDB Storage");
        return ResponseEntity.ok(stats);
    }

    // 7. Web Crawler Execution & Stats
    @PostMapping("/crawler/run")
    public ResponseEntity<Map<String, Object>> runCrawler() {
        return ResponseEntity.ok(externalHotelCrawler.runCrawl());
    }

    @GetMapping("/crawler/stats")
    public ResponseEntity<Map<String, Object>> getCrawlerStats() {
        return ResponseEntity.ok(externalHotelCrawler.getCrawlerMetrics());
    }

    @GetMapping("/crawler/rates")
    public ResponseEntity<List<?>> getCrawledRates() {
        return ResponseEntity.ok(externalHotelCrawler.getAllCrawledRates());
    }

    // 8. Autocomplete Stats
    @GetMapping("/autocomplete/stats")
    public ResponseEntity<Map<String, Object>> getAutocompleteStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queryCount", autocompleteService.getQueryCount());
        stats.put("averageLatencyMs", String.format("%.3f ms", autocompleteService.getAverageLatencyMs()));
        stats.put("algorithm", "Redis ZSet Lexicographical Range with In-Memory Trie Fallback");
        return ResponseEntity.ok(stats);
    }
}
