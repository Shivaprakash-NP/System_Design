package com.hotel.system.controller;

import com.hotel.system.components.ratelimiter.TokenBucketRateLimiter;
import com.hotel.system.model.Booking;
import com.hotel.system.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final TokenBucketRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestBody Map<String, String> request,
            Authentication authentication,
            HttpServletRequest servletRequest) {

        String clientIdentifier = authentication != null ? authentication.getName() : servletRequest.getRemoteAddr();

        // Rate limiter check
        if (!rateLimiter.isAllowed(clientIdentifier)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Rate limit exceeded. Please wait a few seconds and try again.",
                            "status", 429,
                            "client", clientIdentifier
                    ));
        }

        try {
            String guestId = request.getOrDefault("guestId", authentication != null ? authentication.getName() : "guest_demo");
            String guestName = request.getOrDefault("guestName", "Guest");
            String guestEmail = authentication != null ? authentication.getName() : request.getOrDefault("guestEmail", "guest@hotel.demo");
            String hotelId = request.get("hotelId");
            String roomId = request.get("roomId");
            LocalDate checkIn = LocalDate.parse(request.get("checkInDate"));
            LocalDate checkOut = LocalDate.parse(request.get("checkOutDate"));
            String idempotencyKey = request.get("idempotencyKey");

            Booking booking = bookingService.createBooking(
                    guestId,
                    guestName,
                    guestEmail,
                    hotelId,
                    roomId,
                    checkIn,
                    checkOut,
                    idempotencyKey
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(booking);

        } catch (IllegalStateException e) {
            // Concurrency conflict / room already booked
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create booking: " + e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(bookingService.getBookingsByGuestEmail(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long id) {
        return bookingService.getBookingById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<?> checkIn(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookingService.checkIn(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id, Authentication authentication) {
        try {
            String user = authentication != null ? authentication.getName() : "system";
            return ResponseEntity.ok(bookingService.cancelBooking(id, user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
