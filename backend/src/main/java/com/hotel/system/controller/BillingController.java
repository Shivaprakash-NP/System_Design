package com.hotel.system.controller;

import com.hotel.system.model.Invoice;
import com.hotel.system.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/checkout/{bookingId}")
    public ResponseEntity<?> checkout(
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            Long bId = Long.parseLong(bookingId.trim());
            String paymentMethod = (body != null && body.containsKey("paymentMethod"))
                    ? body.get("paymentMethod")
                    : "SIMULATED_CARD";
            Invoice invoice = billingService.generateInvoiceAndCheckout(bId, paymentMethod);
            return ResponseEntity.ok(invoice);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid booking identifier format: " + bookingId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<?> getInvoiceById(@PathVariable String invoiceId) {
        try {
            Long invId = Long.parseLong(invoiceId.trim());
            return billingService.getInvoiceById(invId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid invoice identifier format: " + invoiceId));
        }
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<?> getInvoiceByBooking(@PathVariable String bookingId) {
        try {
            Long bId = Long.parseLong(bookingId.trim());
            return billingService.getInvoiceByBookingId(bId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid booking identifier format: " + bookingId));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<Invoice>> getMyInvoices(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(billingService.getInvoicesByGuestEmail(authentication.getName()));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(billingService.getAllInvoices());
    }
}
