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
        resetToDefaults();
    }

    public synchronized void resetToDefaults() {
        ring.clear();
        physicalNodes.clear();
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

        // Find the next node in clockwise direction on the ring
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
     * Computes before-and-after distributions, exact remapped keys, and virtual ring geometry for visualization.
     */
    public synchronized Map<String, Object> measureRemapping(List<String> sampleKeys, String newNode) {
        Map<String, String> beforeConsistent = new HashMap<>();
        Map<String, String> beforeModulo = new HashMap<>();
        Map<String, Integer> beforeDistribution = new LinkedHashMap<>();

        List<String> originalNodes = new ArrayList<>(physicalNodes);
        Collections.sort(originalNodes);
        int nBefore = Math.max(1, originalNodes.size());

        for (String node : originalNodes) {
            beforeDistribution.put(node, 0);
        }

        for (String key : sampleKeys) {
            String targetC = routeRequest(key);
            beforeConsistent.put(key, targetC);
            beforeDistribution.put(targetC, beforeDistribution.getOrDefault(targetC, 0) + 1);

            int modIdx = Math.abs(key.hashCode()) % nBefore;
            beforeModulo.put(key, originalNodes.get(modIdx));
        }

        // Add the new physical node to the ring
        addNode(newNode);

        List<String> afterNodes = new ArrayList<>(physicalNodes);
        Collections.sort(afterNodes);
        int nAfter = afterNodes.size();

        Map<String, Integer> afterDistribution = new LinkedHashMap<>();
        for (String node : afterNodes) {
            afterDistribution.put(node, 0);
        }

        int consistentRemapped = 0;
        int moduloRemapped = 0;
        List<Map<String, String>> remappedDetails = new ArrayList<>();

        for (String key : sampleKeys) {
            String afterC = routeRequest(key);
            afterDistribution.put(afterC, afterDistribution.getOrDefault(afterC, 0) + 1);

            if (!afterC.equals(beforeConsistent.get(key))) {
                consistentRemapped++;
                if (remappedDetails.size() < 25) { // Sample remapped keys for UI display
                    remappedDetails.add(Map.of(
                            "key", key,
                            "fromShard", beforeConsistent.get(key),
                            "toShard", afterC
                    ));
                }
            }

            int modIdxAfter = Math.abs(key.hashCode()) % nAfter;
            String afterM = afterNodes.get(modIdxAfter);
            if (!afterM.equals(beforeModulo.get(key))) {
                moduloRemapped++;
            }
        }

        double consistentRatio = sampleKeys.isEmpty() ? 0 : (double) consistentRemapped / sampleKeys.size() * 100.0;
        double moduloRatio = sampleKeys.isEmpty() ? 0 : (double) moduloRemapped / sampleKeys.size() * 100.0;
        double theoreticalOptimal = (1.0 / nAfter) * 100.0;

        // Generate geometry coordinates for SVG circular ring visualizer (0 to 360 degrees)
        List<Map<String, Object>> ringVisualNodes = new ArrayList<>();
        double angleStep = 360.0 / afterNodes.size();
        for (int i = 0; i < afterNodes.size(); i++) {
            String nodeName = afterNodes.get(i);
            long representativeHash = generateHash(nodeName + "#0");
            double angleDeg = (i * angleStep) % 360;
            ringVisualNodes.add(Map.of(
                    "node", nodeName,
                    "angleDegrees", angleDeg,
                    "representativeHash", representativeHash,
                    "isNew", nodeName.equals(newNode)
            ));
        }

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("sampleKeysCount", sampleKeys.size());
        results.put("newNode", newNode);
        results.put("nodesBefore", nBefore);
        results.put("nodesAfter", nAfter);
        results.put("activeShards", afterNodes);
        results.put("beforeDistribution", beforeDistribution);
        results.put("afterDistribution", afterDistribution);
        results.put("consistentRemappedCount", consistentRemapped);
        results.put("consistentRemappingPercentage", String.format("%.2f%%", consistentRatio));
        results.put("moduloRemappedCount", moduloRemapped);
        results.put("moduloRemappingPercentage", String.format("%.2f%%", moduloRatio));
        results.put("theoreticalOptimalRemap", String.format("%.2f%%", theoreticalOptimal));
        results.put("remappedKeysSample", remappedDetails);
        results.put("ringVisualNodes", ringVisualNodes);

        return results;
    }

    /**
     * Measures key remapping when an existing node is removed, compared against simple modulo hashing.
     */
    public synchronized Map<String, Object> measureRemoval(List<String> sampleKeys, String nodeToRemove) {
        if (!physicalNodes.contains(nodeToRemove)) {
            throw new IllegalArgumentException("Node does not exist in cluster: " + nodeToRemove);
        }
        if (physicalNodes.size() <= 1) {
            throw new IllegalStateException("Cannot remove node: cluster requires at least 1 shard.");
        }

        Map<String, String> beforeConsistent = new HashMap<>();
        Map<String, String> beforeModulo = new HashMap<>();
        Map<String, Integer> beforeDistribution = new LinkedHashMap<>();

        List<String> originalNodes = new ArrayList<>(physicalNodes);
        Collections.sort(originalNodes);
        int nBefore = originalNodes.size();

        for (String node : originalNodes) {
            beforeDistribution.put(node, 0);
        }

        for (String key : sampleKeys) {
            String targetC = routeRequest(key);
            beforeConsistent.put(key, targetC);
            beforeDistribution.put(targetC, beforeDistribution.getOrDefault(targetC, 0) + 1);

            int modIdx = Math.abs(key.hashCode()) % nBefore;
            beforeModulo.put(key, originalNodes.get(modIdx));
        }

        // Remove node from ring
        removeNode(nodeToRemove);

        List<String> afterNodes = new ArrayList<>(physicalNodes);
        Collections.sort(afterNodes);
        int nAfter = afterNodes.size();

        Map<String, Integer> afterDistribution = new LinkedHashMap<>();
        for (String node : afterNodes) {
            afterDistribution.put(node, 0);
        }

        int consistentRemapped = 0;
        int moduloRemapped = 0;
        List<Map<String, String>> remappedDetails = new ArrayList<>();

        for (String key : sampleKeys) {
            String afterC = routeRequest(key);
            afterDistribution.put(afterC, afterDistribution.getOrDefault(afterC, 0) + 1);

            if (!afterC.equals(beforeConsistent.get(key))) {
                consistentRemapped++;
                if (remappedDetails.size() < 30) {
                    remappedDetails.add(Map.of(
                            "key", key,
                            "fromShard", beforeConsistent.get(key),
                            "toShard", afterC
                    ));
                }
            }

            int modIdxAfter = Math.abs(key.hashCode()) % nAfter;
            String afterM = afterNodes.get(modIdxAfter);
            if (!afterM.equals(beforeModulo.get(key))) {
                moduloRemapped++;
            }
        }

        double consistentRatio = sampleKeys.isEmpty() ? 0 : (double) consistentRemapped / sampleKeys.size() * 100.0;
        double moduloRatio = sampleKeys.isEmpty() ? 0 : (double) moduloRemapped / sampleKeys.size() * 100.0;
        double theoreticalOptimal = (1.0 / nBefore) * 100.0;

        List<Map<String, Object>> ringVisualNodes = new ArrayList<>();
        double angleStep = afterNodes.isEmpty() ? 360.0 : 360.0 / afterNodes.size();
        for (int i = 0; i < afterNodes.size(); i++) {
            String nodeName = afterNodes.get(i);
            long representativeHash = generateHash(nodeName + "#0");
            double angleDeg = (i * angleStep) % 360;
            ringVisualNodes.add(Map.of(
                    "node", nodeName,
                    "angleDegrees", angleDeg,
                    "representativeHash", representativeHash,
                    "isNew", false
            ));
        }

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("sampleKeysCount", sampleKeys.size());
        results.put("action", "REMOVE_SHARD");
        results.put("removedNode", nodeToRemove);
        results.put("nodesBefore", nBefore);
        results.put("nodesAfter", nAfter);
        results.put("activeShards", afterNodes);
        results.put("beforeDistribution", beforeDistribution);
        results.put("afterDistribution", afterDistribution);
        results.put("consistentRemappedCount", consistentRemapped);
        results.put("consistentRemappingPercentage", String.format("%.2f%%", consistentRatio));
        results.put("moduloRemappedCount", moduloRemapped);
        results.put("moduloRemappingPercentage", String.format("%.2f%%", moduloRatio));
        results.put("theoreticalOptimalRemap", String.format("%.2f%%", theoreticalOptimal));
        results.put("remappedKeysSample", remappedDetails);
        results.put("ringVisualNodes", ringVisualNodes);

        return results;
    }

    public List<Map<String, Object>> getRingGeometry() {
        List<String> nodes = new ArrayList<>(physicalNodes);
        Collections.sort(nodes);
        List<Map<String, Object>> geometry = new ArrayList<>();
        double angleStep = nodes.isEmpty() ? 360 : 360.0 / nodes.size();
        for (int i = 0; i < nodes.size(); i++) {
            String node = nodes.get(i);
            geometry.add(Map.of(
                    "node", node,
                    "angleDegrees", (i * angleStep) % 360,
                    "representativeHash", generateHash(node + "#0")
            ));
        }
        return geometry;
    }

    public long generateHash(String key) {
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
