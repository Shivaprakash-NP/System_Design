package com.hotel.system.repository;

import com.hotel.system.model.Hotel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends MongoRepository<Hotel, String> {
    List<Hotel> findByCityIgnoreCase(String city);
    List<Hotel> findByNameContainingIgnoreCase(String name);
}
