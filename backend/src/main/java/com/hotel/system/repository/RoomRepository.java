package com.hotel.system.repository;

import com.hotel.system.model.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {
    List<Room> findByHotelId(String hotelId);
    List<Room> findByHotelIdAndIsAvailableTrue(String hotelId);
    List<Room> findByHousekeepingStatus(String housekeepingStatus);
}
