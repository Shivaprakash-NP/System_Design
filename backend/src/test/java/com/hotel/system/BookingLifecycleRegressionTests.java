package com.hotel.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.system.controller.BookingController;
import com.hotel.system.model.Booking;
import com.hotel.system.model.Room;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.RoomRepository;
import com.hotel.system.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BookingLifecycleRegressionTests {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingController bookingController;

    @Autowired
    private ObjectMapper objectMapper;

    private String testRoomId;

    @BeforeEach
    void setUp() {
        // Create an isolated fresh test room in MongoDB for each test execution
        Room room = roomRepository.save(Room.builder()
                .hotelId("hotel_101")
                .roomNumber("REG_" + UUID.randomUUID().toString().substring(0, 6))
                .type("DELUXE")
                .pricePerNight(200.0)
                .capacity(2)
                .isAvailable(true)
                .housekeepingStatus("CLEAN")
                .build());
        testRoomId = room.getId();
    }

    @Test
    @DisplayName("Regression Test 1: Snowflake 64-bit ID serializes to JSON as String to prevent JS IEEE-754 truncation")
    void testBookingCreationAndSnowflakePrecisionStringSerialization() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(60);
        LocalDate checkOut = checkIn.plusDays(3);
        String idempotencyKey = "reg-test-" + UUID.randomUUID();

        Booking booking = bookingService.createBooking(
                "guest_reg_1",
                "Regression Tester",
                "reg_test@hotel.demo",
                "hotel_101",
                testRoomId,
                checkIn,
                checkOut,
                idempotencyKey
        );

        assertNotNull(booking);
        assertNotNull(booking.getId());

        // A 64-bit Snowflake ID exceeds 2^53 - 1 (9,007,199,254,740,991)
        long idVal = booking.getId();
        assertTrue(idVal > 9007199254740991L, "Snowflake ID must be a 64-bit integer > MAX_SAFE_INTEGER");

        // Serialize to JSON and verify Jackson formats it as a JSON String
        String json = objectMapper.writeValueAsString(booking);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("id"), "JSON must contain 'id' field");
        JsonNode idNode = root.get("id");
        assertTrue(idNode.isTextual(), "JSON 'id' field MUST be serialized as Textual String to prevent JavaScript float64 rounding");
        assertEquals(String.valueOf(idVal), idNode.asText(), "Serialized String ID must match original Long value exactly without precision loss");
    }

    @Test
    @DisplayName("Regression Test 2: Booking lookup by exact 64-bit ID succeeds and preserves persistence")
    void testBookingLookupByExact64BitId() {
        LocalDate checkIn = LocalDate.now().plusDays(70);
        LocalDate checkOut = checkIn.plusDays(2);
        String idempotencyKey = "reg-lookup-" + UUID.randomUUID();

        Booking created = bookingService.createBooking(
                "guest_reg_2",
                "Alice Query",
                "alice_query@hotel.demo",
                "hotel_101",
                testRoomId,
                checkIn,
                checkOut,
                idempotencyKey
        );

        Long bookingId = created.getId();
        Optional<Booking> fetched = bookingService.getBookingById(bookingId);

        assertTrue(fetched.isPresent(), "Booking must be retrievable by its exact 64-bit ID");
        assertEquals(bookingId, fetched.get().getId());
        assertEquals("CONFIRMED", fetched.get().getStatus());
        assertEquals("alice_query@hotel.demo", fetched.get().getGuestEmail());
    }

    @Test
    @DisplayName("Regression Test 3: Check-in succeeds on valid 64-bit ID and updates status to CHECKED_IN")
    void testBookingCheckInWithValidId() {
        LocalDate checkIn = LocalDate.now().plusDays(80);
        LocalDate checkOut = checkIn.plusDays(4);
        String idempotencyKey = "reg-checkin-" + UUID.randomUUID();

        Booking created = bookingService.createBooking(
                "guest_reg_3",
                "Bob Checkin",
                "bob_checkin@hotel.demo",
                "hotel_101",
                testRoomId,
                checkIn,
                checkOut,
                idempotencyKey
        );

        Long bookingId = created.getId();
        Booking checkedIn = bookingService.checkIn(bookingId);

        assertEquals("CHECKED_IN", checkedIn.getStatus());

        // Verify database persistence of status
        Booking inDb = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals("CHECKED_IN", inDb.getStatus());
    }

    @Test
    @DisplayName("Regression Test 4: Check-in with non-existent ID throws IllegalArgumentException")
    void testBookingCheckInNonExistentId() {
        Long bogusId = 987654321987654321L;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.checkIn(bogusId);
        });
        assertTrue(ex.getMessage().contains("Booking not found: " + bogusId));
    }

    @Test
    @DisplayName("Regression Test 5: Controller string-safe ID handling for valid, not found, and malformed IDs")
    void testControllerStringSafeIdEndpoints() {
        LocalDate checkIn = LocalDate.now().plusDays(90);
        LocalDate checkOut = checkIn.plusDays(2);
        String idempotencyKey = "reg-ctrl-" + UUID.randomUUID();

        Booking created = bookingService.createBooking(
                "guest_reg_4",
                "Carol Controller",
                "carol_ctrl@hotel.demo",
                "hotel_101",
                testRoomId,
                checkIn,
                checkOut,
                idempotencyKey
        );

        String idStr = String.valueOf(created.getId());

        // 1. Valid lookup via String
        ResponseEntity<?> resValid = bookingController.getBookingById(idStr);
        assertEquals(HttpStatus.OK, resValid.getStatusCode());

        // 2. Non-existent ID lookup via String
        ResponseEntity<?> resNotFound = bookingController.getBookingById("987654321987654321");
        assertEquals(HttpStatus.NOT_FOUND, resNotFound.getStatusCode());

        // 3. Malformed non-numeric ID
        ResponseEntity<?> resMalformed = bookingController.getBookingById("invalid-id-xyz");
        assertEquals(HttpStatus.BAD_REQUEST, resMalformed.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> errBody = (Map<String, Object>) resMalformed.getBody();
        assertNotNull(errBody);
        assertTrue(errBody.get("error").toString().contains("Invalid booking identifier format"));

        // 4. Check-in via String endpoint
        ResponseEntity<?> resCheckIn = bookingController.checkIn(idStr);
        assertEquals(HttpStatus.OK, resCheckIn.getStatusCode());

        // 5. Malformed Check-in
        ResponseEntity<?> resBadCheckin = bookingController.checkIn("abc-not-an-id");
        assertEquals(HttpStatus.BAD_REQUEST, resBadCheckin.getStatusCode());
    }
}
