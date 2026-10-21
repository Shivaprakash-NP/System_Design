package com.hotel.system.service;

import com.hotel.system.model.Hotel;
import com.hotel.system.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final StringRedisTemplate redisTemplate;

    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    public Optional<Hotel> getHotelById(String id) {
        return hotelRepository.findById(id);
    }

    public List<Hotel> searchHotels(String query, String city, String amenity) {
        List<Hotel> all = hotelRepository.findAll();

        return all.stream()
                .filter(h -> {
                    if (city != null && !city.trim().isEmpty()) {
                        if (!h.getCity().equalsIgnoreCase(city.trim())) {
                            return false;
                        }
                    }
                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.toLowerCase().trim();
                        boolean matchesName = h.getName().toLowerCase().contains(q);
                        boolean matchesCity = h.getCity().toLowerCase().contains(q);
                        boolean matchesDesc = h.getDescription() != null && h.getDescription().toLowerCase().contains(q);
                        if (!matchesName && !matchesCity && !matchesDesc) {
                            return false;
                        }
                    }
                    if (amenity != null && !amenity.trim().isEmpty()) {
                        if (h.getAmenities() == null || h.getAmenities().stream().noneMatch(a -> a.equalsIgnoreCase(amenity.trim()))) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public Hotel createHotel(Hotel hotel) {
        return hotelRepository.save(hotel);
    }
}
