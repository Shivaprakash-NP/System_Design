package com.hotel.system.config;

import com.hotel.system.components.autocomplete.AutocompleteService;
import com.hotel.system.model.Hotel;
import com.hotel.system.model.Notification;
import com.hotel.system.model.Room;
import com.hotel.system.model.User;
import com.hotel.system.repository.BookingRepository;
import com.hotel.system.repository.HotelRepository;
import com.hotel.system.repository.NotificationRepository;
import com.hotel.system.repository.RoomRepository;
import com.hotel.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final AutocompleteService autocompleteService;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@hotel.demo}")
    private String adminEmail;
    @Value("${admin.password:Admin@12345}")
    private String adminPassword;

    @Value("${receptionist.email:reception@hotel.demo}")
    private String receptionistEmail;
    @Value("${receptionist.password:Reception@12345}")
    private String receptionistPassword;

    @Value("${housekeeping.email:housekeeping@hotel.demo}")
    private String housekeepingEmail;
    @Value("${housekeeping.password:Housekeep@12345}")
    private String housekeepingPassword;

    @Value("${guest.email:guest@hotel.demo}")
    private String guestEmail;
    @Value("${guest.password:Guest@12345}")
    private String guestPassword;

    @Override
    public void run(String... args) {
        seedUsers();
        seedHotelsAndRooms();
        seedAutocomplete();
        seedInitialNotifications();
    }

    private void seedUsers() {
        seedUser(adminEmail, adminPassword, "System Administrator", "ADMIN", "+91-9840100001");
        seedUser(receptionistEmail, receptionistPassword, "Front Desk Receptionist", "RECEPTIONIST", "+91-9840100002");
        seedUser(housekeepingEmail, housekeepingPassword, "Housekeeping Supervisor", "HOUSEKEEPING", "+91-9840100003");
        seedUser(guestEmail, guestPassword, "John Guest", "GUEST", "+91-9840100004");
    }

    private void seedUser(String email, String password, String fullName, String role, String phone) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .role(role)
                    .phoneNumber(phone)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            logger.info("Seeded demo user [{}] with role [{}]", email, role);
        }
    }

    private void seedHotelsAndRooms() {
        if (hotelRepository.count() == 0) {
            Hotel h1 = Hotel.builder()
                    .id("hotel_101")
                    .name("Grand Palace Chennai")
                    .city("Chennai")
                    .address("123 Anna Salai, Guindy, Chennai")
                    .description("Luxury 5-star hotel near tech corridor and airport")
                    .amenities(List.of("Free WiFi", "Swimming Pool", "Fitness Center", "Spa", "Free Breakfast", "Airport Shuttle"))
                    .rating(4.8)
                    .imageUrl("https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800")
                    .build();

            Hotel h2 = Hotel.builder()
                    .id("hotel_102")
                    .name("Taj Coromandel")
                    .city("Chennai")
                    .address("37 Mahatma Gandhi Rd, Nungambakkam, Chennai")
                    .description("Iconic heritage hotel with world-class fine dining and hospitality")
                    .amenities(List.of("Free WiFi", "Swimming Pool", "Spa", "Fine Dining", "Valet Parking"))
                    .rating(4.9)
                    .imageUrl("https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800")
                    .build();

            Hotel h3 = Hotel.builder()
                    .id("hotel_201")
                    .name("ITC Grand Central Mumbai")
                    .city("Mumbai")
                    .address("287 Dr Babasaheb Ambedkar Rd, Parel, Mumbai")
                    .description("Majestic luxury hotel reflecting vintage Victorian architecture")
                    .amenities(List.of("Free WiFi", "Infinity Pool", "Sky Lounge", "Business Center", "Spa"))
                    .rating(4.7)
                    .imageUrl("https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?w=800")
                    .build();

            Hotel h4 = Hotel.builder()
                    .id("hotel_301")
                    .name("The Leela Palace Bengaluru")
                    .city("Bengaluru")
                    .address("23 HAL Old Airport Rd, Kodihalli, Bengaluru")
                    .description("Palatial royal luxury amid 7 acres of lush tranquil gardens")
                    .amenities(List.of("Free WiFi", "Royal Spa", "Outdoor Heated Pool", "Tennis Court", "Banquet"))
                    .rating(4.9)
                    .imageUrl("https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=800")
                    .build();

            hotelRepository.saveAll(List.of(h1, h2, h3, h4));
            logger.info("Seeded 4 hotels across Chennai, Mumbai, Bengaluru");
        }

        // Ensure EVERY hotel has at least four distinct room types: SINGLE, DOUBLE, DELUXE, SUITE
        ensureHotelInventory("hotel_101", List.of(
            Room.builder().hotelId("hotel_101").roomNumber("101").type("SINGLE").pricePerNight(95.0).capacity(1).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Ramesh").amenities(List.of("Queen Bed", "City View", "Work Desk")).build(),
            Room.builder().hotelId("hotel_101").roomNumber("102").type("DOUBLE").pricePerNight(145.0).capacity(2).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Priya").amenities(List.of("King Bed", "Balcony", "Minibar")).build(),
            Room.builder().hotelId("hotel_101").roomNumber("103").type("DELUXE").pricePerNight(190.0).capacity(2).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Karthik").amenities(List.of("King Bed", "Pool View", "Espresso Machine")).build(),
            Room.builder().hotelId("hotel_101").roomNumber("104").type("SUITE").pricePerNight(280.0).capacity(4).isAvailable(true).housekeepingStatus("INSPECTED").assignedStaff("Deepa").amenities(List.of("King Bed", "Living Room", "Jacuzzi")).build()
        ));

        ensureHotelInventory("hotel_102", List.of(
            Room.builder().hotelId("hotel_102").roomNumber("201").type("SINGLE").pricePerNight(120.0).capacity(1).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Anand").amenities(List.of("Heritage Single Bed", "WiFi", "Workstation")).build(),
            Room.builder().hotelId("hotel_102").roomNumber("202").type("DOUBLE").pricePerNight(185.0).capacity(2).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Sneha").amenities(List.of("Heritage King Bed", "City View", "Tea Lounge")).build(),
            Room.builder().hotelId("hotel_102").roomNumber("203").type("DELUXE").pricePerNight(240.0).capacity(3).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Vikram").amenities(List.of("Club Deluxe Bed", "Balcony", "Espresso Machine")).build(),
            Room.builder().hotelId("hotel_102").roomNumber("204").type("SUITE").pricePerNight(360.0).capacity(4).isAvailable(true).housekeepingStatus("INSPECTED").assignedStaff("Meena").amenities(List.of("Presidential Suite", "Butler Service", "Jacuzzi")).build()
        ));

        ensureHotelInventory("hotel_201", List.of(
            Room.builder().hotelId("hotel_201").roomNumber("301").type("SINGLE").pricePerNight(110.0).capacity(1).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Sanjay").amenities(List.of("Queen Bed", "Sea Breeze", "Executive Desk")).build(),
            Room.builder().hotelId("hotel_201").roomNumber("302").type("DOUBLE").pricePerNight(175.0).capacity(2).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Anita").amenities(List.of("King Bed", "Sea View", "Minibar")).build(),
            Room.builder().hotelId("hotel_201").roomNumber("303").type("DELUXE").pricePerNight(230.0).capacity(3).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Rahul").amenities(List.of("Oceanfront Deluxe", "Balcony", "Espresso Bar")).build(),
            Room.builder().hotelId("hotel_201").roomNumber("304").type("SUITE").pricePerNight(340.0).capacity(4).isAvailable(true).housekeepingStatus("INSPECTED").assignedStaff("Sunita").amenities(List.of("Skyline Penthouse", "Lounge Access", "Jacuzzi")).build()
        ));

        ensureHotelInventory("hotel_301", List.of(
            Room.builder().hotelId("hotel_301").roomNumber("401").type("SINGLE").pricePerNight(130.0).capacity(1).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Ganesh").amenities(List.of("Palace Single Room", "Garden View", "Ergonomic Desk")).build(),
            Room.builder().hotelId("hotel_301").roomNumber("402").type("DOUBLE").pricePerNight(210.0).capacity(2).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Lakshmi").amenities(List.of("Garden Terrace Double", "Luxury Bath", "Minibar")).build(),
            Room.builder().hotelId("hotel_301").roomNumber("403").type("DELUXE").pricePerNight(290.0).capacity(3).isAvailable(true).housekeepingStatus("CLEAN").assignedStaff("Mohan").amenities(List.of("Royal Deluxe Suite", "Pool View", "Espresso Machine")).build(),
            Room.builder().hotelId("hotel_301").roomNumber("404").type("SUITE").pricePerNight(420.0).capacity(4).isAvailable(true).housekeepingStatus("INSPECTED").assignedStaff("Shalini").amenities(List.of("Royal Maharaja Suite", "Chandelier", "Private Jacuzzi")).build()
        ));

        // Ensure room availability and housekeeping status reflect occupancy:
        // Rooms not currently occupied by active CHECKED_IN guests are marked CLEAN and ready for booking
        List<Room> allRooms = roomRepository.findAll();
        List<com.hotel.system.model.Booking> checkedInBookings = bookingRepository.findAll().stream()
                .filter(b -> "CHECKED_IN".equalsIgnoreCase(b.getStatus()))
                .toList();
        java.util.Set<String> occupiedRoomIds = checkedInBookings.stream()
                .map(com.hotel.system.model.Booking::getRoomId)
                .collect(java.util.stream.Collectors.toSet());

        for (Room r : allRooms) {
            if (occupiedRoomIds.contains(r.getId())) {
                r.setHousekeepingStatus("DIRTY");
                r.setAvailable(false);
            } else if ("DIRTY".equalsIgnoreCase(r.getHousekeepingStatus()) || r.getHousekeepingStatus() == null) {
                r.setHousekeepingStatus("CLEAN");
                r.setAvailable(true);
            } else {
                boolean ready = "CLEAN".equalsIgnoreCase(r.getHousekeepingStatus()) || "INSPECTED".equalsIgnoreCase(r.getHousekeepingStatus());
                r.setAvailable(ready);
            }
            roomRepository.save(r);
        }
    }

    private void ensureHotelInventory(String hotelId, List<Room> templateRooms) {
        List<Room> existing = roomRepository.findByHotelId(hotelId);
        java.util.Set<String> existingTypes = existing.stream()
                .map(r -> r.getType().toUpperCase())
                .collect(java.util.stream.Collectors.toSet());

        for (Room template : templateRooms) {
            if (!existingTypes.contains(template.getType().toUpperCase())) {
                roomRepository.save(template);
                logger.info("Added missing room type [{}] for hotel [{}] Room #{}", template.getType(), hotelId, template.getRoomNumber());
            }
        }
    }

    private void seedAutocomplete() {
        try {
            List<String> terms = List.of(
                "Grand Palace Chennai",
                "Taj Coromandel",
                "ITC Grand Central Mumbai",
                "The Leela Palace Bengaluru",
                "Chennai",
                "Mumbai",
                "Bengaluru",
                "Single Room",
                "Double Room",
                "Suite Room",
                "Deluxe Room",
                "Hilton Chennai",
                "Hyatt Regency Chennai"
            );
            for (String term : terms) {
                autocompleteService.addSuggestion(term);
            }
            logger.info("Seeded Redis Autocomplete terms");
        } catch (Exception e) {
            logger.warn("Could not seed Redis autocomplete: {}", e.getMessage());
        }
    }

    private void seedInitialNotifications() {
        if (notificationRepository.count() == 0) {
            Notification n1 = Notification.builder()
                    .recipientEmail(guestEmail)
                    .recipientName("John Guest")
                    .title("Welcome to Booking.com")
                    .message("Welcome! Enjoy your stay with premier accommodations and seamless reservation service.")
                    .type("WELCOME")
                    .isRead(false)
                    .deliveryChannel("IN_APP")
                    .deliveryStatus("DELIVERED")
                    .build();
            notificationRepository.save(n1);
        }
    }
}
