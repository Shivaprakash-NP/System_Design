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
@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password; // BCrypt encoded
    private String fullName;
    private String role; // GUEST, RECEPTIONIST, HOUSEKEEPING, ADMIN
    private String phoneNumber;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
