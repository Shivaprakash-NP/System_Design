package com.hotel.system.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long invoiceId; // Generated via Snowflake (64-bit precision safe)
    
    @Indexed
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
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
