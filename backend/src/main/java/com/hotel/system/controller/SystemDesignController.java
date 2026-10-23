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

    // 1. Rate Limiter Stats & Audit Trace
    @GetMapping("/rate-limiter/stats")
    public ResponseEntity<Map<String, Object>> getRateLimiterStats(
            @RequestParam(defaultValue = "guest@hotel.demo") String client) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("allowedRequests", rateLimiter.getAllowedRequests());
        stats.put("rejectedRequests", rateLimiter.getRejectedRequests());
        stats.put("rejectionRatePercentage", String.format("%.2f%%", rateLimiter.getRejectionRate()));
        stats.put("totalTokensConsumed", rateLimiter.getTotalTokensConsumed());
        stats.put("averageLatencyMs", String.format("%.3f ms", rateLimiter.getAverageLatencyMs()));
        stats.put("lastCheckLatencyMs", String.format("%.3f ms", rateLimiter.getLastCheckLatencyMs()));

        long currentTokens = rateLimiter.getAvailableTokens(client, rateLimiter.getDefaultCapacity(), rateLimiter.getDefaultRefillRate());
        stats.put("currentTokens", currentTokens);
        stats.put("capacity", rateLimiter.getDefaultCapacity());
        stats.put("refillRate", rateLimiter.getDefaultRefillRate());
        stats.put("monitoredClient", client);
        stats.put("recentRequests", rateLimiter.getRecentAuditLogs());
        stats.put("algorithm", "Token Bucket (Redis Lua with In-memory Fallback)");
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/rate-limiter/current-tokens")
    public ResponseEntity<Map<String, Object>> getCurrentTokens(
            @RequestParam(defaultValue = "sim_user") String client,
            @RequestParam(defaultValue = "10") int capacity,
            @RequestParam(defaultValue = "2") int refillRate) {
        long tokens = rateLimiter.getAvailableTokens(client, capacity, refillRate);
        return ResponseEntity.ok(Map.of(
                "client", client,
                "currentTokens", tokens,
                "capacity", capacity,
                "refillRate", refillRate
        ));
    }

    @PostMapping("/rate-limiter/reset")
    public ResponseEntity<Void> resetRateLimiter() {
        rateLimiter.resetMetrics();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/rate-limiter/test")
    public ResponseEntity<Map<String, Object>> testRateLimiter(
            @RequestParam(defaultValue = "sim_user") String clientId,
            @RequestParam(defaultValue = "5") int burst,
            @RequestParam(defaultValue = "10") int capacity,
            @RequestParam(defaultValue = "2") int refillRate) {

        List<Map<String, Object>> results = new ArrayList<>();
        int allowed = 0;
        int rejected = 0;
        long lastRemaining = 0;
        double totalBurstLatency = 0.0;

        for (int i = 0; i < burst; i++) {
            TokenBucketRateLimiter.TokenConsumptionResult cr = rateLimiter.consumeToken(clientId, capacity, refillRate, 1);
            boolean isAllowed = cr.allowed();
            lastRemaining = cr.remainingTokens();
            totalBurstLatency += cr.latencyMs();

            if (isAllowed) allowed++;
            else rejected++;

            int status = isAllowed ? 200 : 429;
            String text = isAllowed
                    ? String.format("200 OK (Allowed - Token Consumed, %d left)", cr.remainingTokens())
                    : "429 Rate Limit Exceeded (Tokens Depleted)";

            results.add(Map.of(
                    "requestIndex", i + 1,
                    "allowed", isAllowed,
                    "status", status,
                    "text", text,
                    "tokensRemaining", cr.remainingTokens(),
                    "latencyMs", Double.parseDouble(String.format("%.3f", cr.latencyMs())),
                    "timestamp", System.currentTimeMillis() + i
            ));

            rateLimiter.recordAudit(
                    clientId,
                    "POST",
                    "/api/v1/system-design/rate-limiter/test",
                    "BURST_SIMULATION",
                    isAllowed,
                    status,
                    cr.remainingTokens(),
                    cr.latencyMs()
            );
        }

        double avgBurstLatency = burst > 0 ? (totalBurstLatency / burst) : 0.0;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("clientId", clientId);
        resp.put("burstCount", burst);
        resp.put("allowedCount", allowed);
        resp.put("rejectedCount", rejected);
        resp.put("remainingTokens", lastRemaining);
        resp.put("capacity", capacity);
        resp.put("refillRate", refillRate);
        resp.put("averageBurstLatencyMs", String.format("%.3f ms", avgBurstLatency));
        resp.put("requests", results);
        resp.put("backendEngine", "Redis Lua Atomic Script");
        return ResponseEntity.ok(resp);
    }

    // 2. Sharding Statistics & Routing
    @GetMapping("/sharding/stats")
    public ResponseEntity<Map<String, Object>> getShardingStats() {
        return ResponseEntity.ok(shardingService.getShardDistributionStatistics());
    }

    @GetMapping("/sharding/route")
    public ResponseEntity<Map<String, Object>> routeShardKey(@RequestParam(defaultValue = "hotel_101") String key) {
        long start = System.nanoTime();
        String targetShard = shardingService.routeShardForHotel(key);
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
        return ResponseEntity.ok(Map.of(
                "key", key,
                "targetShard", targetShard,
                "latencyMs", String.format("%.3f ms", latencyMs),
                "strategy", "Consistent Hash Ring with 100 V-Nodes per Host"
        ));
    }

    @PostMapping("/sharding/add-shard")
    public ResponseEntity<Map<String, Object>> addShard(@RequestParam(defaultValue = "shard_2") String shardName) {
        return ResponseEntity.ok(shardingService.addShard(shardName));
    }

    @PostMapping("/sharding/remove-shard")
    public ResponseEntity<Map<String, Object>> removeShard(@RequestParam(defaultValue = "shard_2") String shardName) {
        return ResponseEntity.ok(shardingService.removeShard(shardName));
    }

    @PostMapping("/sharding/reset")
    public ResponseEntity<Map<String, Object>> resetSharding() {
        return ResponseEntity.ok(shardingService.resetRing());
    }

    // 3. Consistent Hashing Evaluation
    @GetMapping("/consistent-hashing/stats")
    public ResponseEntity<Map<String, Object>> getConsistentHashingStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("physicalNodes", consistentHashRing.getPhysicalNodes());
        stats.put("virtualNodesCount", consistentHashRing.getRingSize());
        stats.put("virtualNodesPerPhysical", 100);
        stats.put("ringVisualNodes", consistentHashRing.getRingGeometry());
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/consistent-hashing/test-remapping")
    public ResponseEntity<Map<String, Object>> testRemapping(
            @RequestParam(defaultValue = "node_delta") String newNode,
            @RequestParam(defaultValue = "100") int sampleKeysCount) {

        List<String> keys = shardingService.getAllRealDbKeys();
        if (keys.isEmpty()) {
            keys.add("hotel:hotel_101 (SSN Grand Hotel)");
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
        stats.put("redisAverageLatencyMs", String.format("%.3f ms", roomService.getAverageRedisLatencyMs()));
        stats.put("mongoAverageLatencyMs", String.format("%.3f ms", roomService.getAverageMongoLatencyMs()));
        stats.put("averageLookupLatencyMs", String.format("%.3f ms", roomService.getAverageLookupLatencyMs()));
        stats.put("cacheBackend", "Redis 7.0 In-Memory Store (with MongoDB WiredTiger Fallback)");
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/cache/probe")
    public ResponseEntity<Map<String, Object>> probeCache(@RequestParam(defaultValue = "hotel_101") String hotelId) {
        return ResponseEntity.ok(roomService.probeCache(hotelId));
    }

    @PostMapping("/cache/evict")
    public ResponseEntity<Map<String, Object>> evictCache(@RequestParam(defaultValue = "hotel_101") String hotelId) {
        return ResponseEntity.ok(roomService.evictCache(hotelId));
    }

    @PostMapping("/cache/reset")
    public ResponseEntity<Void> resetCacheMetrics() {
        roomService.resetCacheMetrics();
        return ResponseEntity.ok().build();
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
    @PostMapping("/crawler/crawl")
    public ResponseEntity<ExternalHotelCrawler.CrawlReport> crawlUrl(@RequestBody(required = false) Map<String, Object> request) {
        String seedUrl = (request != null && request.containsKey("seedUrl"))
                ? (String) request.get("seedUrl")
                : "https://example.com";
        int maxPages = (request != null && request.get("maxPages") != null)
                ? Integer.parseInt(request.get("maxPages").toString())
                : 5;
        int maxDepth = (request != null && request.get("maxDepth") != null)
                ? Integer.parseInt(request.get("maxDepth").toString())
                : 2;
        return ResponseEntity.ok(externalHotelCrawler.crawlUrl(seedUrl, maxPages, maxDepth));
    }

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
        long hits = autocompleteService.getHits();
        long misses = autocompleteService.getMisses();
        long total = hits + misses;
        double hitRatio = total > 0 ? (hits * 100.0 / total) : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queryCount", total);
        stats.put("hits", hits);
        stats.put("misses", misses);
        stats.put("hitRatioPercentage", String.format("%.2f%%", hitRatio));
        stats.put("averageLatencyMs", String.format("%.3f ms", autocompleteService.getAverageLatencyMs()));
        stats.put("indexedTermsCount", autocompleteService.getIndexSize());
        stats.put("algorithm", "Redis ZSet Lexicographical Range with In-Memory Trie Fallback");
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/autocomplete/reset")
    public ResponseEntity<Void> resetAutocompleteMetrics() {
        autocompleteService.resetMetrics();
        return ResponseEntity.ok().build();
    }
}
