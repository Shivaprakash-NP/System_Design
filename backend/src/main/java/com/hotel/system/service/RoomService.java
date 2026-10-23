package com.hotel.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.system.model.Booking;
import com.hotel.system.model.Room;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Real cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong redisLookupTimeNanos = new AtomicLong(0);
    private final AtomicLong mongoLookupTimeNanos = new AtomicLong(0);
    private final AtomicLong totalLookupTimeNanos = new AtomicLong(0);

    public List<Room> getAvailableRooms(String hotelId) {
        return getAvailableRooms(hotelId, null);
    }

    public List<Room> getAvailableRooms(String hotelId, String sortBy) {
        long start = System.nanoTime();
        String cacheKey = "rooms:available:" + hotelId;
        
        try {
            String cachedRooms = redisTemplate.opsForValue().get(cacheKey);
            if (cachedRooms != null) {
                long elapsed = System.nanoTime() - start;
                cacheHits.incrementAndGet();
                redisLookupTimeNanos.addAndGet(elapsed);
                totalLookupTimeNanos.addAndGet(elapsed);
                List<Room> cachedList = objectMapper.readValue(cachedRooms,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Room.class));
                return sortRooms(new java.util.ArrayList<>(cachedList), sortBy);
            }
        } catch (Exception e) {
            logger.warn("Redis cache read failed, falling back to database: {}", e.getMessage());
        }

        // Cache Miss: Query MongoDB
        long mongoStart = System.nanoTime();
        List<Room> rooms = roomRepository.findByHotelIdAndIsAvailableTrue(hotelId);
        if (rooms.isEmpty()) {
            rooms = roomRepository.findByHotelId(hotelId);
        }
        long mongoElapsed = System.nanoTime() - mongoStart;
        long totalElapsed = System.nanoTime() - start;

        cacheMisses.incrementAndGet();
        mongoLookupTimeNanos.addAndGet(mongoElapsed);
        totalLookupTimeNanos.addAndGet(totalElapsed);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(rooms), Duration.ofMinutes(10));
        } catch (Exception e) {
            logger.warn("Failed to write to Redis cache: {}", e.getMessage());
        }

        return sortRooms(new java.util.ArrayList<>(rooms), sortBy);
    }

    public List<Room> getAvailableRoomsForDates(String hotelId, LocalDate checkIn, LocalDate checkOut) {
        return getAvailableRoomsForDates(hotelId, checkIn, checkOut, null);
    }

    public List<Room> getAvailableRoomsForDates(String hotelId, LocalDate checkIn, LocalDate checkOut, String sortBy) {
        List<Room> allRooms = roomRepository.findByHotelId(hotelId);

        List<Room> available = allRooms.stream()
                .filter(room -> isRoomAvailableForDates(room.getId(), checkIn, checkOut))
                .collect(Collectors.toList());

        return sortRooms(available, sortBy);
    }

    public List<Room> sortRooms(List<Room> rooms, String sortBy) {
        if (sortBy == null || rooms == null || rooms.isEmpty()) return rooms;
        switch (sortBy.toLowerCase()) {
            case "price_asc":
            case "price-asc":
                rooms.sort(java.util.Comparator.comparingDouble(Room::getPricePerNight));
                break;
            case "price_desc":
            case "price-desc":
                rooms.sort(java.util.Comparator.comparingDouble(Room::getPricePerNight).reversed());
                break;
            case "capacity_desc":
            case "capacity-desc":
            case "size_desc":
                rooms.sort(java.util.Comparator.comparingInt(Room::getCapacity).reversed());
                break;
            case "capacity_asc":
            case "capacity-asc":
            case "size_asc":
                rooms.sort(java.util.Comparator.comparingInt(Room::getCapacity));
                break;
            case "room_number":
            case "roomnumber":
                rooms.sort((r1, r2) -> {
                    try {
                        return Integer.compare(Integer.parseInt(r1.getRoomNumber()), Integer.parseInt(r2.getRoomNumber()));
                    } catch (Exception e) {
                        return r1.getRoomNumber().compareTo(r2.getRoomNumber());
                    }
                });
                break;
            default:
                break;
        }
        return rooms;
    }

    public boolean isRoomAvailableForDates(String roomId, LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return false;
        }

        List<Booking> activeBookings = bookingRepository.findByRoomIdAndStatusIn(
                roomId,
                List.of("CONFIRMED", "CHECKED_IN")
        );

        // Check half-open interval overlap: [checkIn, checkOut)
        // Two intervals [A_start, A_end) and [B_start, B_end) overlap iff:
        // A_start < B_end && A_end > B_start
        for (Booking b : activeBookings) {
            if (b.getCheckInDate().isBefore(checkOut) && b.getCheckOutDate().isAfter(checkIn)) {
                return false; // Overlap detected
            }
        }

        return true;
    }

    public void updateHousekeepingStatus(String roomId, String status, boolean isAvailable, String assignedStaff) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        room.setHousekeepingStatus(status.toUpperCase());
        room.setAvailable(isAvailable);
        if (assignedStaff != null) {
            room.setAssignedStaff(assignedStaff);
        }
        roomRepository.save(room);

        // Invalidate Redis cache
        evictHotelCache(room.getHotelId());
    }

    public void evictHotelCache(String hotelId) {
        try {
            redisTemplate.delete("rooms:available:" + hotelId);
        } catch (Exception e) {
            logger.warn("Failed to evict Redis cache for hotel {}: {}", hotelId, e.getMessage());
        }
    }

    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public List<Room> getRoomsByHousekeepingStatus(String status) {
        return roomRepository.findByHousekeepingStatus(status.toUpperCase());
    }

    public Room saveRoom(Room room) {
        Room saved = roomRepository.save(room);
        evictHotelCache(saved.getHotelId());
        return saved;
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public double getAverageRedisLatencyMs() {
        long hits = cacheHits.get();
        if (hits == 0) return 0.0;
        return (redisLookupTimeNanos.get() / (double) hits) / 1_000_000.0;
    }

    public double getAverageMongoLatencyMs() {
        long misses = cacheMisses.get();
        if (misses == 0) return 0.0;
        return (mongoLookupTimeNanos.get() / (double) misses) / 1_000_000.0;
    }

    public double getAverageLookupLatencyMs() {
        long total = cacheHits.get() + cacheMisses.get();
        if (total == 0) return 0.0;
        return (totalLookupTimeNanos.get() / (double) total) / 1_000_000.0;
    }

    public void resetCacheMetrics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        redisLookupTimeNanos.set(0);
        mongoLookupTimeNanos.set(0);
        totalLookupTimeNanos.set(0);
    }

    public Map<String, Object> probeCache(String hotelId) {
        if (hotelId == null || hotelId.trim().isEmpty()) {
            hotelId = "hotel_101";
        }
        String cacheKey = "rooms:available:" + hotelId;
        long start = System.nanoTime();
        
        try {
            String cachedRooms = redisTemplate.opsForValue().get(cacheKey);
            if (cachedRooms != null) {
                long elapsedNanos = System.nanoTime() - start;
                cacheHits.incrementAndGet();
                redisLookupTimeNanos.addAndGet(elapsedNanos);
                totalLookupTimeNanos.addAndGet(elapsedNanos);
                double latencyMs = elapsedNanos / 1_000_000.0;
                
                List<Room> cachedList = objectMapper.readValue(cachedRooms,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Room.class));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("key", cacheKey);
                result.put("type", "HIT");
                result.put("latency", String.format("%.3f ms", latencyMs));
                result.put("latencyMs", latencyMs);
                result.put("backend", "Redis In-Memory (Key-Value)");
                result.put("cachedEntityCount", cachedList.size());
                result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                return result;
            }
        } catch (Exception e) {
            logger.warn("Redis probe failed: {}", e.getMessage());
        }

        // Cache Miss -> Read MongoDB
        long mongoStart = System.nanoTime();
        List<Room> rooms = roomRepository.findByHotelIdAndIsAvailableTrue(hotelId);
        if (rooms.isEmpty()) {
            rooms = roomRepository.findByHotelId(hotelId);
        }
        long mongoElapsed = System.nanoTime() - mongoStart;
        long totalElapsed = System.nanoTime() - start;

        cacheMisses.incrementAndGet();
        mongoLookupTimeNanos.addAndGet(mongoElapsed);
        totalLookupTimeNanos.addAndGet(totalElapsed);
        double latencyMs = totalElapsed / 1_000_000.0;

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(rooms), Duration.ofMinutes(10));
        } catch (Exception e) {
            logger.warn("Redis write failed: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", cacheKey);
        result.put("type", "MISS");
        result.put("latency", String.format("%.3f ms", latencyMs));
        result.put("latencyMs", latencyMs);
        result.put("backend", "MongoDB Disk Fetch (WiredTiger)");
        result.put("cachedEntityCount", rooms.size());
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        return result;
    }

    public Map<String, Object> evictCache(String hotelId) {
        if (hotelId == null || hotelId.trim().isEmpty()) {
            hotelId = "hotel_101";
        }
        String cacheKey = "rooms:available:" + hotelId;
        long start = System.nanoTime();
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            long elapsedNanos = System.nanoTime() - start;
            double latencyMs = elapsedNanos / 1_000_000.0;
            return Map.of(
                    "key", cacheKey,
                    "type", "PURGE",
                    "latency", String.format("%.3f ms", latencyMs),
                    "backend", "Redis DEL Mutation",
                    "deleted", Boolean.TRUE.equals(deleted),
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
        } catch (Exception e) {
            return Map.of("key", cacheKey, "type", "ERROR", "error", e.getMessage());
        }
    }
}
