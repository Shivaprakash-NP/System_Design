package com.hotel.system.repository;

import com.hotel.system.model.Booking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends MongoRepository<Booking, Long> {
    List<Booking> findByGuestId(String guestId);
    List<Booking> findByGuestEmail(String guestEmail);
    List<Booking> findByHotelId(String hotelId);
    List<Booking> findByRoomId(String roomId);
    List<Booking> findByRoomIdAndStatusIn(String roomId, List<String> statuses);
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
