package com.hotel.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rooms")
public class Room {
    @Id
    private String id;
    
    @Indexed
    private String hotelId;
    
    private String roomNumber;
    private String type; // SINGLE, DOUBLE, SUITE, DELUXE
    private double pricePerNight;
    private int capacity;
    private boolean isAvailable;
    private String housekeepingStatus; // CLEAN, DIRTY, CLEANING, INSPECTED
    private String assignedStaff;
    private List<String> amenities;
}
