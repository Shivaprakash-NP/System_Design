package com.hotel.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "invoices")
public class Invoice {
    @Id
    private Long invoiceId; // Generated via Snowflake
    
    @Indexed
    private Long bookingId;
    
    @Indexed
    private String guestId;
    private String guestName;
    private String guestEmail;

    private String roomNumber;
    private String roomType;
    
    private double roomCharges;
    private double taxAmount;
    private double serviceCharges;
    private double totalAmount;
    
    private String status; // PAID, PENDING
    private String paymentMethod;
    
    @Builder.Default
    private LocalDateTime issuedAt = LocalDateTime.now();
    
    private String invoiceUrl; // Shortened URL for guest notification
    private String shortCode;
}
