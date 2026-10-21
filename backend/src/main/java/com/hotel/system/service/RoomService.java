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
import java.util.List;
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

    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalLookupTimeNanos = new AtomicLong(0);

    public List<Room> getAvailableRooms(String hotelId) {
        long start = System.nanoTime();
        String cacheKey = "rooms:available:" + hotelId;
        
        try {
            String cachedRooms = redisTemplate.opsForValue().get(cacheKey);
            if (cachedRooms != null) {
                cacheHits.incrementAndGet();
                totalLookupTimeNanos.addAndGet(System.nanoTime() - start);
                return objectMapper.readValue(cachedRooms,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Room.class));
            }
        } catch (Exception e) {
            logger.warn("Redis cache read failed, falling back to database: {}", e.getMessage());
        }

        // Cache Miss: Query MongoDB
        cacheMisses.incrementAndGet();
        List<Room> rooms = roomRepository.findByHotelIdAndIsAvailableTrue(hotelId);
        totalLookupTimeNanos.addAndGet(System.nanoTime() - start);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(rooms), Duration.ofMinutes(10));
        } catch (Exception e) {
            logger.warn("Failed to write to Redis cache: {}", e.getMessage());
        }

        return rooms;
    }

    public List<Room> getAvailableRoomsForDates(String hotelId, LocalDate checkIn, LocalDate checkOut) {
        List<Room> allRooms = roomRepository.findByHotelId(hotelId);

        return allRooms.stream()
                .filter(room -> isRoomAvailableForDates(room.getId(), checkIn, checkOut))
                .collect(Collectors.toList());
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

    public double getAverageLookupLatencyMs() {
        long total = cacheHits.get() + cacheMisses.get();
        if (total == 0) return 0.0;
        return (totalLookupTimeNanos.get() / (double) total) / 1_000_000.0;
    }
}
