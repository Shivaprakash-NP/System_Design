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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bookings")
public class Booking {
    @Id
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id; // Generated via SnowflakeIdGenerator (64-bit precision safe)
    
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
    @Builder.Default
    private double baseRoomAmount = 0.0;
    @Builder.Default
    private double taxAmount = 0.0; // 12% GST
    @Builder.Default
    private double serviceFee = 0.0;
    private double totalAmount; // Total stay price including 12% GST and service fees
    
    @Indexed
    private String status; // CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED

    @Indexed
    private String idempotencyKey;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
