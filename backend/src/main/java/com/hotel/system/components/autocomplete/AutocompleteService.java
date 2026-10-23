package com.hotel.system.components.autocomplete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AutocompleteService {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteService.class);

    private final StringRedisTemplate redisTemplate;
    private static final String AUTOCOMPLETE_KEY = "hotel:autocomplete";

    // Local in-memory Trie fallback in case Redis is offline
    private final TrieNode root = new TrieNode();

    // Metrics
    private final AtomicLong queryHits = new AtomicLong(0);
    private final AtomicLong queryMisses = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong indexedCount = new AtomicLong(0);

    public AutocompleteService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addSuggestion(String term) {
        if (term == null || term.trim().isEmpty()) return;

        String normalizedTerm = term.trim().toLowerCase();

        // 1. Add to local Trie
        insertIntoTrie(normalizedTerm, term.trim());
        indexedCount.incrementAndGet();

        // 2. Add to Redis ZSet (full term with score 0 for lexicographical indexing)
        try {
            redisTemplate.opsForZSet().add(AUTOCOMPLETE_KEY, normalizedTerm, 0);
        } catch (Exception e) {
            logger.warn("Could not insert autocomplete into Redis, saved to in-memory Trie: {}", e.getMessage());
        }
    }

    public Set<String> getSuggestions(String prefix, int maxResults) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Set.of();
        }

        long start = System.nanoTime();
        String normalizedPrefix = prefix.trim().toLowerCase();

        try {
            Range<String> range = Range.rightOpen(normalizedPrefix, normalizedPrefix + "\uffff");
            Set<String> results = redisTemplate.opsForZSet().rangeByLex(
                    AUTOCOMPLETE_KEY,
                    range,
                    RedisZSetCommands.Limit.limit().count(maxResults)
            );

            if (results != null && !results.isEmpty()) {
                queryHits.incrementAndGet();
                totalLatencyNanos.addAndGet(System.nanoTime() - start);
                return results;
            }
        } catch (Exception e) {
            logger.warn("Redis autocomplete query failed, using in-memory Trie fallback: {}", e.getMessage());
        }

        // Fallback to in-memory Trie
        Set<String> trieResults = searchTrie(normalizedPrefix, maxResults);
        long elapsed = System.nanoTime() - start;
        totalLatencyNanos.addAndGet(elapsed);

        if (trieResults != null && !trieResults.isEmpty()) {
            queryHits.incrementAndGet();
            return trieResults;
        } else {
            queryMisses.incrementAndGet();
            return Set.of();
        }
    }

    public double getAverageLatencyMs() {
        long total = queryHits.get() + queryMisses.get();
        if (total == 0) return 0.0;
        return (totalLatencyNanos.get() / (double) total) / 1_000_000.0;
    }

    public long getQueryCount() {
        return queryHits.get() + queryMisses.get();
    }

    public long getHits() {
        return queryHits.get();
    }

    public long getMisses() {
        return queryMisses.get();
    }

    public long getIndexSize() {
        try {
            Long count = redisTemplate.opsForZSet().zCard(AUTOCOMPLETE_KEY);
            if (count != null && count > 0) return count;
        } catch (Exception ignored) {
        }
        return indexedCount.get();
    }

    public void resetMetrics() {
        queryHits.set(0);
        queryMisses.set(0);
        totalLatencyNanos.set(0);
    }

    // --- In-memory Trie Fallback Implementation ---

    private static class TrieNode {
        Map<Character, TrieNode> children = new ConcurrentHashMap<>();
        boolean isEndOfWord = false;
        String originalTerm = null;
    }

    private void insertIntoTrie(String normalized, String original) {
        TrieNode current = root;
        for (char ch : normalized.toCharArray()) {
            current = current.children.computeIfAbsent(ch, c -> new TrieNode());
        }
        current.isEndOfWord = true;
        current.originalTerm = original;
    }

    private Set<String> searchTrie(String prefix, int maxResults) {
        TrieNode current = root;
        for (char ch : prefix.toCharArray()) {
            current = current.children.get(ch);
            if (current == null) return Set.of();
        }
        Set<String> results = new LinkedHashSet<>();
        collectTrieWords(current, results, maxResults);
        return results;
    }

    private void collectTrieWords(TrieNode node, Set<String> results, int limit) {
        if (results.size() >= limit) return;
        if (node.isEndOfWord && node.originalTerm != null) {
            results.add(node.originalTerm);
        }
        for (TrieNode child : node.children.values()) {
            collectTrieWords(child, results, limit);
            if (results.size() >= limit) break;
        }
    }
}
