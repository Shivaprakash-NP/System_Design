package com.hotel.system;

import com.hotel.system.components.autocomplete.AutocompleteService;
import com.hotel.system.components.crawler.ExternalHotelCrawler;
import com.hotel.system.components.idgenerator.SnowflakeIdGenerator;
import com.hotel.system.components.ratelimiter.TokenBucketRateLimiter;
import com.hotel.system.components.router.ConsistentHashRing;
import com.hotel.system.components.urlshortener.UrlShortenerService;
import com.hotel.system.model.ShortUrl;
import com.hotel.system.service.ShardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SystemDesignComponentsTests {

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    @Autowired
    private ConsistentHashRing consistentHashRing;

    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Autowired
    private ExternalHotelCrawler externalHotelCrawler;

    @Autowired
    private AutocompleteService autocompleteService;

    @Autowired
    private ShardingService shardingService;

    @Test
    void testRateLimiterEnforcesBurstCapacity() {
        String testUser = "rate_limit_test_" + UUID.randomUUID();
        int capacity = 5;
        int refillRate = 1; // 1 token per sec

        // First 5 requests must succeed
        for (int i = 0; i < capacity; i++) {
            assertTrue(rateLimiter.isAllowed(testUser, capacity, refillRate), 
                    "Request " + (i + 1) + " should be allowed under burst capacity");
        }

        // 6th request immediately after must be rejected (429)
        assertFalse(rateLimiter.isAllowed(testUser, capacity, refillRate), 
                "Request beyond capacity must be rejected by Rate Limiter");

        assertTrue(rateLimiter.getRejectedRequests() > 0);
    }

    @Test
    void testSnowflakeIdCollisionFreeConcurrently() throws InterruptedException {
        int totalIds = 20000;
        int threads = 10;
        Set<Long> set = ConcurrentHashMap.newKeySet(totalIds);
        AtomicInteger collisions = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        int idsPerThread = totalIds / threads;

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        long id = idGenerator.generateId();
                        if (!set.add(id)) {
                            collisions.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, collisions.get(), "Snowflake generator must produce 0 duplicate IDs");
        assertEquals(totalIds, set.size(), "All generated IDs must be strictly unique");
    }

    @Test
    void testConsistentHashRingDistributionAndRemapping() {
        ConsistentHashRing ring = new ConsistentHashRing(100);
        ring.addNode("node_A");
        ring.addNode("node_B");
        ring.addNode("node_C");

        assertTrue(ring.getPhysicalNodes().containsAll(List.of("node_A", "node_B", "node_C")));
        assertTrue(ring.getRingSize() >= 300);

        // Test request routing
        String route1 = ring.routeRequest("hotel_123");
        String route2 = ring.routeRequest("hotel_123");
        assertEquals(route1, route2, "Same key must deterministically route to the exact same node");

        // Measure remapping when adding a 4th node with real-style entity keys
        List<String> sampleKeys = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            sampleKeys.add("hotel:hotel_" + i + " [room:#" + (100 + i) + "]");
        }
        Map<String, Object> remapping = ring.measureRemapping(sampleKeys, "node_D");
        assertNotNull(remapping);
        int consistentRemapped = (int) remapping.get("consistentRemappedCount");
        int moduloRemapped = (int) remapping.get("moduloRemappedCount");

        // Consistent hashing should remap significantly fewer keys than simple modulo!
        assertTrue(consistentRemapped < moduloRemapped, 
                "Consistent hashing (" + consistentRemapped + ") must remap fewer keys than simple modulo (" + moduloRemapped + ")");

        // Measure remapping when removing a node
        Map<String, Object> removal = ring.measureRemoval(sampleKeys, "node_D");
        assertNotNull(removal);
        int consistentRemovedRemap = (int) removal.get("consistentRemappedCount");
        int moduloRemovedRemap = (int) removal.get("moduloRemappedCount");
        assertTrue(consistentRemovedRemap <= moduloRemovedRemap,
                "Consistent hashing removal (" + consistentRemovedRemap + ") must remap fewer or equal keys than modulo (" + moduloRemovedRemap + ")");
    }

    @Test
    void testUrlShortenerAndRedirection() {
        String longUrl = "http://localhost:5173/invoice/9876543210";
        ShortUrl shortUrl = urlShortenerService.createShortUrl(longUrl, null, "admin@hotel.demo");
        assertNotNull(shortUrl.getShortCode());

        // Resolve
        Optional<String> resolved = urlShortenerService.resolve(shortUrl.getShortCode());
        assertTrue(resolved.isPresent());
        assertEquals(longUrl, resolved.get());

        // Non-existent code returns empty
        Optional<String> missing = urlShortenerService.resolve("non_existent_code_xyz");
        assertTrue(missing.isEmpty());
    }

    @Test
    void testWebCrawlerExtractsCompetitorRates() {
        Map<String, Object> crawlResult = externalHotelCrawler.runCrawl();
        assertTrue("SUCCESS".equals(crawlResult.get("status")) || "COMPLETED".equals(crawlResult.get("status")));
        assertTrue((int) crawlResult.get("pagesCrawled") >= 1);
        assertTrue((int) crawlResult.get("recordsImported") >= 2);

        Map<String, Object> metrics = externalHotelCrawler.getCrawlerMetrics();
        assertTrue(((Number) metrics.get("successfulPages")).intValue() >= 1);
    }

    @Test
    void testAutocompletePrefixSuggestions() {
        autocompleteService.addSuggestion("Taj Coromandel");
        autocompleteService.addSuggestion("Taj Exotica");
        autocompleteService.addSuggestion("Taj Mahal Palace");

        Set<String> suggestions = autocompleteService.getSuggestions("taj", 10);
        assertNotNull(suggestions);
        assertTrue(suggestions.size() >= 3, "Autocomplete should return all matching prefixes for 'taj'");
        assertTrue(suggestions.stream().allMatch(s -> s.toLowerCase().startsWith("taj")));
    }

    @Test
    void testShardingStatistics() {
        Map<String, Object> stats = shardingService.getShardDistributionStatistics();
        assertNotNull(stats.get("shardBreakdown"));
        assertTrue((int) stats.get("totalDocuments") > 0);
        assertTrue((int) stats.get("activeShardsCount") >= 2);
    }
}
