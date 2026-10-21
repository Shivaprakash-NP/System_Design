package com.hotel.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "competitor_rates")
public class ImportedCompetitorRate {
    @Id
    private String id;

    @Indexed
    private String competitorName;

    private String sourceUrl;
    private String roomType;
    private double observedPrice;
    private String currency;
    private List<String> amenities;
    private double rating;
    private int reviewCount;
    private String status; // SUCCESS, PARSE_ERROR, FETCH_FAILED

    @Builder.Default
    private LocalDateTime crawledAt = LocalDateTime.now();
}
