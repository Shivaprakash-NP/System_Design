package com.hotel.system.service;

import com.hotel.system.components.router.ConsistentHashRing;
import com.hotel.system.model.Booking;
import com.hotel.system.model.Hotel;
import com.hotel.system.model.Room;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.HotelRepository;
import com.hotel.system.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ShardingService {

    private final ConsistentHashRing hashRing;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    // Simulated per-shard record storage counts
    private final Map<String, Long> shardDocumentCounts = new ConcurrentHashMap<>();

    public String routeShardForHotel(String hotelId) {
        return hashRing.routeRequest(hotelId);
    }

    public Map<String, Object> getShardDistributionStatistics() {
        List<Hotel> hotels = hotelRepository.findAll();
        List<Room> rooms = roomRepository.findAll();
        List<Booking> bookings = bookingRepository.findAll();

        Map<String, Map<String, Integer>> shardBreakdown = new LinkedHashMap<>();
        for (String node : hashRing.getPhysicalNodes()) {
            shardBreakdown.put(node, new HashMap<>(Map.of("hotels", 0, "rooms", 0, "bookings", 0, "total", 0)));
        }

        // Map hotels
        for (Hotel h : hotels) {
            String shard = routeShardForHotel(h.getId());
            shardBreakdown.computeIfAbsent(shard, k -> new HashMap<>(Map.of("hotels", 0, "rooms", 0, "bookings", 0, "total", 0)));
            Map<String, Integer> counts = shardBreakdown.get(shard);
            counts.put("hotels", counts.get("hotels") + 1);
            counts.put("total", counts.get("total") + 1);
        }

        // Map rooms (routed by hotelId)
        for (Room r : rooms) {
            String shard = routeShardForHotel(r.getHotelId());
            shardBreakdown.computeIfAbsent(shard, k -> new HashMap<>(Map.of("hotels", 0, "rooms", 0, "bookings", 0, "total", 0)));
            Map<String, Integer> counts = shardBreakdown.get(shard);
            counts.put("rooms", counts.get("rooms") + 1);
            counts.put("total", counts.get("total") + 1);
        }

        // Map bookings (routed by hotelId)
        for (Booking b : bookings) {
            String shard = routeShardForHotel(b.getHotelId());
            shardBreakdown.computeIfAbsent(shard, k -> new HashMap<>(Map.of("hotels", 0, "rooms", 0, "bookings", 0, "total", 0)));
            Map<String, Integer> counts = shardBreakdown.get(shard);
            counts.put("bookings", counts.get("bookings") + 1);
            counts.put("total", counts.get("total") + 1);
        }

        int grandTotal = hotels.size() + rooms.size() + bookings.size();

        // Calculate imbalance metrics
        int maxRecords = 0;
        int minRecords = Integer.MAX_VALUE;
        for (Map<String, Integer> counts : shardBreakdown.values()) {
            int total = counts.get("total");
            if (total > maxRecords) maxRecords = total;
            if (total < minRecords) minRecords = total;
        }

        double imbalanceRatio = (minRecords > 0 && minRecords != Integer.MAX_VALUE)
                ? ((double) maxRecords / minRecords) - 1.0
                : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDocuments", grandTotal);
        stats.put("activeShardsCount", hashRing.getPhysicalNodes().size());
        stats.put("activeShards", hashRing.getPhysicalNodes());
        stats.put("shardBreakdown", shardBreakdown);
        stats.put("maxShardRecords", maxRecords);
        stats.put("minShardRecords", minRecords == Integer.MAX_VALUE ? 0 : minRecords);
        stats.put("imbalanceFactor", String.format("%.2f%%", imbalanceRatio * 100.0));
        stats.put("architectureNote", "Application-level logical partitioning routes entities by shard key (hotelId) across logical namespaces. Distinguishes from native MongoDB mongos cluster sharding.");

        return stats;
    }

    public List<String> getAllRealDbKeys() {
        List<String> realKeys = new ArrayList<>();
        
        // 1. Real Hotels stored in MongoDB
        for (Hotel h : hotelRepository.findAll()) {
            realKeys.add("hotel:" + h.getId() + " (" + h.getName() + ")");
        }

        // 2. Real Rooms stored in MongoDB
        for (Room r : roomRepository.findAll()) {
            realKeys.add("room:" + r.getHotelId() + ":#" + r.getRoomNumber() + " [" + r.getType() + "]");
        }

        // 3. Real Bookings stored in MongoDB
        for (Booking b : bookingRepository.findAll()) {
            realKeys.add("booking:#" + b.getId() + " (" + b.getGuestName() + ")");
        }

        return realKeys;
    }

    public Map<String, Object> addShard(String newShardName) {
        List<String> realKeys = getAllRealDbKeys();
        Map<String, Object> remappingResults = hashRing.measureRemapping(realKeys, newShardName);
        remappingResults.put("action", "ADD_SHARD");
        remappingResults.put("newShardName", newShardName);
        remappingResults.put("isRealDbData", true);
        return remappingResults;
    }

    public Map<String, Object> resetRing() {
        hashRing.resetToDefaults();
        return Map.of(
                "action", "RESET_RING",
                "activeShards", hashRing.getPhysicalNodes(),
                "ringVisualNodes", hashRing.getRingGeometry()
        );
    }

    public Map<String, Object> removeShard(String shardName) {
        List<String> realKeys = getAllRealDbKeys();
        Map<String, Object> remappingResults = hashRing.measureRemoval(realKeys, shardName);
        remappingResults.put("action", "REMOVE_SHARD");
        remappingResults.put("removedShard", shardName);
        remappingResults.put("isRealDbData", true);
        return remappingResults;
    }
}
