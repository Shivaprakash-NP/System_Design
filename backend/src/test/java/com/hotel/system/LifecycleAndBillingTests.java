package com.hotel.system;

import com.hotel.system.model.Booking;
import com.hotel.system.model.Invoice;
import com.hotel.system.model.Notification;
import com.hotel.system.model.Room;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.NotificationRepository;
import com.hotel.system.repository.RoomRepository;
import com.hotel.system.service.BillingService;
import com.hotel.system.service.BookingService;
import com.hotel.system.service.HousekeepingService;
import com.hotel.system.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LifecycleAndBillingTests {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private HousekeepingService housekeepingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Room room;

    @BeforeEach
    void setUp() {
        room = roomRepository.findAll().stream().findFirst().orElseThrow();
    }

    @Test
    void testCheckInCheckOutAndBillingWorkflow() {
        LocalDate checkIn = LocalDate.now().plusDays(60);
        LocalDate checkOut = LocalDate.now().plusDays(63); // 3 nights

        Booking booking = bookingService.createBooking(
                "guest_bill_test", "Alice Wonder", "alice@test.demo",
                room.getHotelId(), room.getId(),
                checkIn, checkOut, null
        );

        assertEquals("CONFIRMED", booking.getStatus());
        double expectedRoomCharges = 3 * room.getPricePerNight();
        assertEquals(expectedRoomCharges, booking.getTotalAmount(), 0.01);

        // 1. Check In
        Booking checkedIn = bookingService.checkIn(booking.getId());
        assertEquals("CHECKED_IN", checkedIn.getStatus());

        // 2. Check Out & Generate Invoice
        Invoice invoice = billingService.generateInvoiceAndCheckout(booking.getId(), "SIMULATED_CARD");
        assertNotNull(invoice.getInvoiceId());
        assertEquals(booking.getId(), invoice.getBookingId());
        assertEquals("PAID", invoice.getStatus());

        // Verify itemized calculations
        assertEquals(expectedRoomCharges, invoice.getRoomCharges(), 0.01);
        double expectedTax = Math.round(expectedRoomCharges * 0.12 * 100.0) / 100.0;
        assertEquals(expectedTax, invoice.getTaxAmount(), 0.01);
        assertEquals(15.00, invoice.getServiceCharges(), 0.01);
        assertEquals(expectedRoomCharges + expectedTax + 15.00, invoice.getTotalAmount(), 0.01);

        // Verify room housekeeping was set to DIRTY upon checkout
        Room updatedRoom = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals("DIRTY", updatedRoom.getHousekeepingStatus());
        assertFalse(updatedRoom.isAvailable(), "Dirty room must not be available for immediate assignment");

        // 3. Housekeeping state transitions: DIRTY -> CLEANING -> CLEAN -> INSPECTED
        Room cleaningRoom = housekeepingService.updateRoomHousekeeping(room.getId(), "CLEANING", "Staff Dave");
        assertEquals("CLEANING", cleaningRoom.getHousekeepingStatus());
        assertFalse(cleaningRoom.isAvailable());

        Room cleanRoom = housekeepingService.updateRoomHousekeeping(room.getId(), "CLEAN", "Staff Dave");
        assertEquals("CLEAN", cleanRoom.getHousekeepingStatus());
        assertTrue(cleanRoom.isAvailable());

        Room inspectedRoom = housekeepingService.updateRoomHousekeeping(room.getId(), "INSPECTED", "Supervisor Mary");
        assertEquals("INSPECTED", inspectedRoom.getHousekeepingStatus());
        assertTrue(inspectedRoom.isAvailable());
    }

    @Test
    void testNotificationsDeliveredToGuest() {
        notificationService.sendNotification(
                "guest_notif@test.demo",
                "Guest Notification",
                "Special Promotion",
                "Complimentary breakfast during your upcoming stay",
                "PROMO",
                12345L
        );

        List<Notification> notifs = notificationService.getNotificationsForUser("guest_notif@test.demo");
        assertFalse(notifs.isEmpty());
        Notification latest = notifs.get(0);
        assertEquals("Special Promotion", latest.getTitle());
        assertFalse(latest.isRead());

        // Mark as read
        notificationService.markAsRead(latest.getId());
        List<Notification> updated = notificationService.getNotificationsForUser("guest_notif@test.demo");
        assertTrue(updated.get(0).isRead());
    }
}
