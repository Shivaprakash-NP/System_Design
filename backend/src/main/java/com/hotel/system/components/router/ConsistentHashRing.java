package com.hotel.system.components.router;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Component
public class ConsistentHashRing {

    private final ConcurrentSkipListMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final Set<String> physicalNodes = ConcurrentHashMap.newKeySet();
    private final int numberOfReplicas; // Virtual nodes per physical node

    public ConsistentHashRing() {
        this(100);
    }

    public ConsistentHashRing(int numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
        // Default physical shard nodes
        addNode("shard_0");
        addNode("shard_1");
    }

    public synchronized void addNode(String node) {
        physicalNodes.add(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = generateHash(node + "#" + i);
            ring.put(hash, node);
        }
    }

    public synchronized void removeNode(String node) {
        physicalNodes.remove(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = generateHash(node + "#" + i);
            ring.remove(hash);
        }
    }

    public String routeRequest(String requestKey) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = generateHash(requestKey);

        // Find the next node in the ring
        Long targetHash = ring.ceilingKey(hash);
        if (targetHash == null) {
            targetHash = ring.firstKey();
        }
        return ring.get(targetHash);
    }

    public Set<String> getPhysicalNodes() {
        return Collections.unmodifiableSet(physicalNodes);
    }

    public int getRingSize() {
        return ring.size();
    }

    /**
     * Measures key remapping when a new node is added, compared against simple modulo hashing.
     */
    public Map<String, Object> measureRemapping(List<String> sampleKeys, String newNode) {
        Map<String, String> beforeConsistent = new HashMap<>();
        Map<String, String> beforeModulo = new HashMap<>();

        List<String> originalNodes = new ArrayList<>(physicalNodes);
        int nBefore = Math.max(1, originalNodes.size());

        for (String key : sampleKeys) {
            beforeConsistent.put(key, routeRequest(key));
            int modIdx = Math.abs(key.hashCode()) % nBefore;
            beforeModulo.put(key, originalNodes.get(modIdx));
        }

        // Add the new node
        addNode(newNode);

        List<String> afterNodes = new ArrayList<>(physicalNodes);
        int nAfter = afterNodes.size();

        int consistentRemapped = 0;
        int moduloRemapped = 0;

        for (String key : sampleKeys) {
            String afterC = routeRequest(key);
            if (!afterC.equals(beforeConsistent.get(key))) {
                consistentRemapped++;
            }

            int modIdxAfter = Math.abs(key.hashCode()) % nAfter;
            String afterM = afterNodes.get(modIdxAfter);
            if (!afterM.equals(beforeModulo.get(key))) {
                moduloRemapped++;
            }
        }

        double consistentRatio = sampleKeys.isEmpty() ? 0 : (double) consistentRemapped / sampleKeys.size() * 100.0;
        double moduloRatio = sampleKeys.isEmpty() ? 0 : (double) moduloRemapped / sampleKeys.size() * 100.0;

        Map<String, Object> results = new HashMap<>();
        results.put("sampleKeysCount", sampleKeys.size());
        results.put("newNode", newNode);
        results.put("nodesBefore", nBefore);
        results.put("nodesAfter", nAfter);
        results.put("consistentRemappedCount", consistentRemapped);
        results.put("consistentRemappingPercentage", String.format("%.2f%%", consistentRatio));
        results.put("moduloRemappedCount", moduloRemapped);
        results.put("moduloRemappingPercentage", String.format("%.2f%%", moduloRatio));
        results.put("theoreticalOptimalRemap", String.format("%.2f%%", (1.0 / nAfter) * 100.0));

        return results;
    }

    private long generateHash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((long) (digest[3] & 0xFF) << 24) |
                   ((long) (digest[2] & 0xFF) << 16) |
                   ((long) (digest[1] & 0xFF) << 8) |
                   ((long) (digest[0] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            return (long) key.hashCode() & 0xFFFFFFFFL;
        }
    }
}
