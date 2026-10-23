package com.hotel.system.service;

import com.hotel.system.components.idgenerator.SnowflakeIdGenerator;
import com.hotel.system.components.urlshortener.UrlShortenerService;
import com.hotel.system.model.Booking;
import com.hotel.system.model.Invoice;
import com.hotel.system.model.Room;
import com.hotel.system.model.ShortUrl;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.InvoiceRepository;
import com.hotel.system.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final UrlShortenerService urlShortenerService;
    private final NotificationService notificationService;
    private final RoomService roomService;

    public Invoice generateInvoiceAndCheckout(Long bookingId, String paymentMethod) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        booking.setStatus("CHECKED_OUT");
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Update Room housekeeping status to DIRTY upon checkout
        if (booking.getRoomId() != null) {
            Optional<Room> roomOpt = roomRepository.findById(booking.getRoomId());
            if (roomOpt.isPresent()) {
                Room r = roomOpt.get();
                r.setHousekeepingStatus("DIRTY");
                r.setAvailable(false);
                roomRepository.save(r);
                roomService.evictHotelCache(r.getHotelId());
            }
        }

        // Calculate itemized charges
        double roomCharges = booking.getBaseRoomAmount() > 0 
                ? booking.getBaseRoomAmount() 
                : (booking.getPricePerNight() * booking.getNumberOfNights());
        double taxAmount = booking.getTaxAmount() > 0 
                ? booking.getTaxAmount() 
                : (Math.round(roomCharges * 0.12 * 100.0) / 100.0); // 12% GST
        double serviceCharges = booking.getServiceFee() > 0 
                ? booking.getServiceFee() 
                : 15.00;
        double total = Math.round((roomCharges + taxAmount + serviceCharges) * 100.0) / 100.0;

        long invoiceId = idGenerator.generateId();
        String shortCode = urlShortenerService.encode(invoiceId);
        String destinationUrl = "/invoice/" + invoiceId;

        ShortUrl shortUrl = urlShortenerService.createShortUrl(destinationUrl, shortCode, booking.getGuestEmail());
        String invoiceUrl = "/r/" + shortCode;

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceId)
                .bookingId(bookingId)
                .guestId(booking.getGuestId())
                .guestName(booking.getGuestName())
                .guestEmail(booking.getGuestEmail())
                .roomNumber(booking.getRoomNumber())
                .roomType(booking.getRoomType())
                .roomCharges(roomCharges)
                .taxAmount(taxAmount)
                .serviceCharges(serviceCharges)
                .totalAmount(total)
                .status("PAID") // Simulated payment
                .paymentMethod(paymentMethod != null ? paymentMethod : "SIMULATED_CARD")
                .issuedAt(LocalDateTime.now())
                .invoiceUrl(invoiceUrl)
                .shortCode(shortCode)
                .build();

        Invoice saved = invoiceRepository.save(invoice);

        notificationService.sendNotification(
                booking.getGuestEmail(),
                booking.getGuestName(),
                "Checkout & Invoice #" + invoiceId,
                "Thank you for staying with us! Your itemized bill of $" + String.format("%.2f", total) 
                        + " has been settled. View receipt: " + invoiceUrl,
                "INVOICE_GENERATED",
                invoiceId
        );

        return saved;
    }

    public Optional<Invoice> getInvoiceById(Long invoiceId) {
        return invoiceRepository.findById(invoiceId);
    }

    public Optional<Invoice> getInvoiceByBookingId(Long bookingId) {
        return invoiceRepository.findByBookingId(bookingId);
    }

    public List<Invoice> getInvoicesByGuestEmail(String email) {
        return invoiceRepository.findByGuestEmail(email);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }
}
