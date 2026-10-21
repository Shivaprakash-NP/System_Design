package com.hotel.system.repository;

import com.hotel.system.model.ImportedCompetitorRate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportedCompetitorRateRepository extends MongoRepository<ImportedCompetitorRate, String> {
    List<ImportedCompetitorRate> findByCompetitorName(String competitorName);
}
