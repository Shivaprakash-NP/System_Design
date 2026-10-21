package com.hotel.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bookings")
public class Booking {
    @Id
    private Long id; // Generated via SnowflakeIdGenerator
    
    @Indexed
    private String hotelId; // Shard key
    
    @Indexed
    private String guestId;

    private String guestName;
    private String guestEmail;
    
    @Indexed
    private String roomId;
    private String roomNumber;
    private String roomType;

    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private long numberOfNights;
    private double pricePerNight;
    private double totalAmount;
    
    @Indexed
    private String status; // CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED

    @Indexed
    private String idempotencyKey;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
