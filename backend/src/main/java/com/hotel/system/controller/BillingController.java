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
            @PathVariable Long bookingId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String paymentMethod = (body != null && body.containsKey("paymentMethod"))
                    ? body.get("paymentMethod")
                    : "SIMULATED_CARD";
            Invoice invoice = billingService.generateInvoiceAndCheckout(bookingId, paymentMethod);
            return ResponseEntity.ok(invoice);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Long invoiceId) {
        return billingService.getInvoiceById(invoiceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<Invoice> getInvoiceByBooking(@PathVariable Long bookingId) {
        return billingService.getInvoiceByBookingId(bookingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
