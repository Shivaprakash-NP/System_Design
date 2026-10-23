package com.hotel.system.service;

import com.hotel.system.components.idgenerator.SnowflakeIdGenerator;
import com.hotel.system.model.Booking;
import com.hotel.system.model.Room;
import com.hotel.system.model.User;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.RoomRepository;
import com.hotel.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationService notificationService;
    private final RoomService roomService;
    private final StringRedisTemplate redisTemplate;

    // Fallback in-memory locks per room in case Redis is unavailable
    private final ConcurrentHashMap<String, ReentrantLock> localRoomLocks = new ConcurrentHashMap<>();

    public Booking createBooking(
            String guestId,
            String guestName,
            String guestEmail,
            String hotelId,
            String roomId,
            LocalDate checkIn,
            LocalDate checkOut,
            String idempotencyKey
    ) {
        // 1. Validate dates
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Check-in and check-out dates are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out date must be strictly after check-in date.");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past.");
        }

        // 2. Check Idempotency Key
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existing.isPresent()) {
                logger.info("Idempotent request received. Returning existing booking [{}]", existing.get().getId());
                return existing.get();
            }
        }

        // 3. Verify Room exists
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        // 4. Acquire Lock for Room Concurrency Safety
        String lockKey = "lock:room:" + roomId;
        String lockValue = UUID.randomUUID().toString();
        boolean acquiredRedisLock = false;

        ReentrantLock localLock = localRoomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());

        try {
            // Spin-wait up to 3000ms to acquire Redis distributed lock
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Boolean setSuccess = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(6));
                    if (Boolean.TRUE.equals(setSuccess)) {
                        acquiredRedisLock = true;
                        break;
                    }
                } catch (Exception e) {
                    // Redis connection issue; fallback to local JVM lock
                    break;
                }
                try {
                    Thread.sleep(40);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Booking lock acquisition interrupted.");
                }
            }

            if (!acquiredRedisLock) {
                // If Redis was down, use local JVM lock
                try {
                    if (!localLock.tryLock(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Room booking in progress. Please retry.");
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Booking lock acquisition interrupted.");
                }
            }

            // CRITICAL SECTION: Verify availability under lock
            List<Booking> overlapping = bookingRepository.findByRoomIdAndStatusIn(
                    roomId,
                    List.of("CONFIRMED", "CHECKED_IN")
            );

            for (Booking b : overlapping) {
                // Half-open interval overlap check: [checkIn, checkOut)
                if (b.getCheckInDate().isBefore(checkOut) && b.getCheckOutDate().isAfter(checkIn)) {
                    throw new IllegalStateException("Room " + room.getRoomNumber() + " is already booked for the selected dates (" 
                            + b.getCheckInDate() + " to " + b.getCheckOutDate() + ").");
                }
            }

            // Calculate nights and itemized amounts with 12% GST
            long nights = Math.max(1, ChronoUnit.DAYS.between(checkIn, checkOut));
            double roomCharges = nights * room.getPricePerNight();
            double gstTax = Math.round(roomCharges * 0.12 * 100.0) / 100.0;
            double serviceFee = 15.00;
            double totalWithGst = Math.round((roomCharges + gstTax + serviceFee) * 100.0) / 100.0;

            long bookingId = idGenerator.generateId();

            Booking booking = Booking.builder()
                    .id(bookingId)
                    .guestId(guestId)
                    .guestName(guestName != null ? guestName : "Guest")
                    .guestEmail(guestEmail != null ? guestEmail : "guest@hotel.demo")
                    .hotelId(hotelId != null ? hotelId : room.getHotelId())
                    .roomId(roomId)
                    .roomNumber(room.getRoomNumber())
                    .roomType(room.getType())
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .numberOfNights(nights)
                    .pricePerNight(room.getPricePerNight())
                    .baseRoomAmount(roomCharges)
                    .taxAmount(gstTax)
                    .serviceFee(serviceFee)
                    .totalAmount(totalWithGst)
                    .status("CONFIRMED")
                    .idempotencyKey(idempotencyKey != null ? idempotencyKey.trim() : null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Booking saved = bookingRepository.save(booking);

            // Invalidate Redis cache
            roomService.evictHotelCache(room.getHotelId());

            // Send notification to Guest
            notificationService.sendNotification(
                    saved.getGuestEmail(),
                    saved.getGuestName(),
                    "Reservation Confirmed #" + saved.getId(),
                    "Your booking for Room " + room.getRoomNumber() + " (" + room.getType() + ") from " 
                            + checkIn + " to " + checkOut + " is confirmed. Total: $" + String.format("%.2f", totalWithGst) + " (incl. 12% GST & fees).",
                    "BOOKING_CONFIRMED",
                    saved.getId()
            );

            // Send notification to Receptionists for Front Desk processing
            notifyReceptionists(
                    "New Reservation: Room " + room.getRoomNumber(),
                    "Booking #" + saved.getId() + " confirmed for Room " + room.getRoomNumber() + " (" + room.getType() 
                            + ") from " + checkIn + " to " + checkOut + ". Guest: " + saved.getGuestName() + ". Total: $" + String.format("%.2f", totalWithGst) + ".",
                    "BOOKING_CONFIRMED",
                    saved.getId()
            );

            return saved;

        } finally {
            // Release Redis lock
            if (acquiredRedisLock) {
                try {
                    String currentVal = redisTemplate.opsForValue().get(lockKey);
                    if (lockValue.equals(currentVal)) {
                        redisTemplate.delete(lockKey);
                    }
                } catch (Exception e) {
                    logger.warn("Error releasing Redis lock: {}", e.getMessage());
                }
            }
            if (localLock.isHeldByCurrentThread()) {
                localLock.unlock();
            }
        }
    }

    public Booking checkIn(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Only CONFIRMED bookings can be checked in. Current status: " + booking.getStatus());
        }

        booking.setStatus("CHECKED_IN");
        booking.setUpdatedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        notificationService.sendNotification(
                booking.getGuestEmail(),
                booking.getGuestName(),
                "Checked In Successfully",
                "Welcome! You have checked into Room " + booking.getRoomNumber() + ". Enjoy your stay!",
                "CHECKED_IN",
                booking.getId()
        );

        return saved;
    }

    public Booking cancelBooking(Long bookingId, String cancelledBy) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if ("CHECKED_IN".equalsIgnoreCase(booking.getStatus()) || "CHECKED_OUT".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Cannot cancel a stay that has already checked in or checked out.");
        }
        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            return booking;
        }

        booking.setStatus("CANCELLED");
        booking.setUpdatedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        roomService.evictHotelCache(booking.getHotelId());

        notificationService.sendNotification(
                booking.getGuestEmail(),
                booking.getGuestName(),
                "Reservation Cancelled #" + booking.getId(),
                "Your reservation for Room " + booking.getRoomNumber() + " has been cancelled.",
                "CANCELLED",
                booking.getId()
        );

        notifyReceptionists(
                "Reservation Cancelled: Room " + booking.getRoomNumber(),
                "Booking #" + booking.getId() + " for Room " + booking.getRoomNumber() + " has been cancelled by guest.",
                "CANCELLED",
                booking.getId()
        );

        return saved;
    }

    private void notifyReceptionists(String title, String message, String type, Long referenceId) {
        try {
            List<User> receptionists = userRepository.findByRole("RECEPTIONIST");
            if (receptionists != null && !receptionists.isEmpty()) {
                for (User rec : receptionists) {
                    notificationService.sendNotification(
                            rec.getEmail(),
                            rec.getFullName(),
                            title,
                            message,
                            type,
                            referenceId
                    );
                }
            } else {
                notificationService.sendNotification(
                        "reception@hotel.demo",
                        "Front Desk",
                        title,
                        message,
                        type,
                        referenceId
                );
            }
        } catch (Exception e) {
            logger.warn("Could not deliver notification to receptionists: {}", e.getMessage());
        }
    }

    public Optional<Booking> getBookingById(Long id) {
        return bookingRepository.findById(id);
    }

    public List<Booking> getBookingsByGuest(String guestId) {
        List<Booking> list = new java.util.ArrayList<>(bookingRepository.findByGuestId(guestId));
        list.sort((a, b) -> {
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
            if (a.getId() != null && b.getId() != null) {
                return b.getId().compareTo(a.getId());
            }
            return 0;
        });
        return list;
    }

    public List<Booking> getBookingsByGuestEmail(String email) {
        List<Booking> list = new java.util.ArrayList<>(bookingRepository.findByGuestEmail(email));
        list.sort((a, b) -> {
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
            if (a.getId() != null && b.getId() != null) {
                return b.getId().compareTo(a.getId());
            }
            return 0;
        });
        return list;
    }

    public List<Booking> getAllBookings() {
        List<Booking> list = new java.util.ArrayList<>(bookingRepository.findAll());
        list.sort((a, b) -> {
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
            if (a.getId() != null && b.getId() != null) {
                return b.getId().compareTo(a.getId());
            }
            return 0;
        });
        return list;
    }
}
