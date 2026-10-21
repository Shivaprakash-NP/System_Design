package com.hotel.system;

import com.hotel.system.model.Booking;
import com.hotel.system.model.Room;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.RoomRepository;
import com.hotel.system.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BookingConcurrencyTests {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    private Room testRoom;

    @BeforeEach
    void setUp() {
        // Find or create test room
        testRoom = roomRepository.findAll().stream().findFirst().orElseGet(() -> {
            Room r = Room.builder()
                    .hotelId("hotel_test")
                    .roomNumber("999")
                    .type("DOUBLE")
                    .pricePerNight(150.0)
                    .capacity(2)
                    .isAvailable(true)
                    .housekeepingStatus("CLEAN")
                    .build();
            return roomRepository.save(r);
        });

        // Clean bookings for this room
        List<Booking> existing = bookingRepository.findByRoomId(testRoom.getId());
        bookingRepository.deleteAll(existing);
    }

    @Test
    void testConcurrentOverlappingBookingsOnlyOneSucceeds() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Long> successfulBookingIds = new CopyOnWriteArrayList<>();

        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(15);

        for (int i = 0; i < threads; i++) {
            final int guestIdx = i;
            executor.submit(() -> {
                try {
                    Booking b = bookingService.createBooking(
                            "guest_" + guestIdx,
                            "Guest " + guestIdx,
                            "guest" + guestIdx + "@test.com",
                            testRoom.getHotelId(),
                            testRoom.getId(),
                            checkIn,
                            checkOut,
                            null
                    );
                    successCount.incrementAndGet();
                    successfulBookingIds.add(b.getId());
                } catch (IllegalStateException e) {
                    // Expected conflict for all concurrent attempts except one
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Unexpected exception in concurrent test: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        // INVARIANT VERIFICATION: Exactly ONE confirmed reservation must succeed!
        assertEquals(1, successCount.get(), "Only exactly one concurrent reservation should succeed for the same room and dates");
        assertEquals(threads - 1, conflictCount.get(), "All other concurrent attempts must be rejected with conflict");

        // Verify database state directly
        List<Booking> persisted = bookingRepository.findByRoomIdAndStatusIn(testRoom.getId(), List.of("CONFIRMED"));
        assertEquals(1, persisted.size(), "Database must contain exactly 1 confirmed reservation");
        assertEquals(successfulBookingIds.get(0), persisted.get(0).getId());
    }

    @Test
    void testNonOverlappingBookingsBothSucceed() {
        LocalDate stay1CheckIn = LocalDate.now().plusDays(20);
        LocalDate stay1CheckOut = LocalDate.now().plusDays(25);

        LocalDate stay2CheckIn = LocalDate.now().plusDays(25); // Half-open interval allows checkIn on previous checkOut day!
        LocalDate stay2CheckOut = LocalDate.now().plusDays(30);

        Booking b1 = bookingService.createBooking(
                "guest_A", "Guest A", "a@test.com",
                testRoom.getHotelId(), testRoom.getId(),
                stay1CheckIn, stay1CheckOut, null
        );

        Booking b2 = bookingService.createBooking(
                "guest_B", "Guest B", "b@test.com",
                testRoom.getHotelId(), testRoom.getId(),
                stay2CheckIn, stay2CheckOut, null
        );

        assertNotNull(b1.getId());
        assertNotNull(b2.getId());
        assertNotEquals(b1.getId(), b2.getId());
        assertEquals("CONFIRMED", b1.getStatus());
        assertEquals("CONFIRMED", b2.getStatus());
    }

    @Test
    void testIdempotencyReturnsSameBooking() {
        String idempotencyKey = "unique-key-xyz-12345";
        LocalDate checkIn = LocalDate.now().plusDays(40);
        LocalDate checkOut = LocalDate.now().plusDays(44);

        Booking b1 = bookingService.createBooking(
                "guest_idem", "Guest Idem", "idem@test.com",
                testRoom.getHotelId(), testRoom.getId(),
                checkIn, checkOut, idempotencyKey
        );

        // Submit again with the same idempotency key
        Booking b2 = bookingService.createBooking(
                "guest_idem", "Guest Idem", "idem@test.com",
                testRoom.getHotelId(), testRoom.getId(),
                checkIn, checkOut, idempotencyKey
        );

        assertEquals(b1.getId(), b2.getId(), "Idempotent requests must return the identical booking without creating duplicate");
    }

    @Test
    void testCancellationAndRebooking() {
        LocalDate checkIn = LocalDate.now().plusDays(50);
        LocalDate checkOut = LocalDate.now().plusDays(55);

        Booking b = bookingService.createBooking(
                "guest_cancel", "Guest Cancel", "cancel@test.com",
                testRoom.getHotelId(), testRoom.getId(),
                checkIn, checkOut, null
        );

        assertEquals("CONFIRMED", b.getStatus());

        // Cancel
        Booking cancelled = bookingService.cancelBooking(b.getId(), "guest_cancel");
        assertEquals("CANCELLED", cancelled.getStatus());

        // Now another guest should be able to book the exact same room and dates
        Booking rebooked = bookingService.createBooking(
                "guest_new", "Guest New", "new@test.com",
                testRoom.getHotelId(), testRoom.getId(),
                checkIn, checkOut, null
        );

        assertNotNull(rebooked.getId());
        assertEquals("CONFIRMED", rebooked.getStatus());
    }
}
