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
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;

    @Indexed
    private String recipientEmail;

    private String recipientName;
    private String title;
    private String message;
    private String type; // BOOKING_CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, INVOICE_GENERATED

    @JsonSerialize(using = ToStringSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long referenceId; // bookingId or invoiceId (64-bit precision safe)
    private boolean isRead;
    private String deliveryChannel; // IN_APP, SIMULATED_EMAIL, SIMULATED_SMS
    private String deliveryStatus; // DELIVERED, PENDING, FAILED

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
