package com.hotel.system.repository;

import com.hotel.system.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends MongoRepository<Invoice, Long> {
    Optional<Invoice> findByBookingId(Long bookingId);
    Optional<Invoice> findByShortCode(String shortCode);
    List<Invoice> findByGuestId(String guestId);
    List<Invoice> findByGuestEmail(String guestEmail);
}
