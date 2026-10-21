package com.hotel.system.service;

import com.hotel.system.model.Room;
import com.hotel.system.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HousekeepingService {

    private final RoomRepository roomRepository;
    private final RoomService roomService;

    public List<Room> getAllRoomsForHousekeeping() {
        return roomRepository.findAll();
    }

    public List<Room> getRoomsByStatus(String status) {
        return roomRepository.findByHousekeepingStatus(status.toUpperCase());
    }

    public Room updateRoomHousekeeping(String roomId, String status, String assignedStaff) {
        String cleanStatus = status.trim().toUpperCase();
        if (!List.of("CLEAN", "DIRTY", "CLEANING", "INSPECTED").contains(cleanStatus)) {
            throw new IllegalArgumentException("Invalid housekeeping status. Must be CLEAN, DIRTY, CLEANING, or INSPECTED.");
        }

        boolean isAvailable = cleanStatus.equals("CLEAN") || cleanStatus.equals("INSPECTED");
        roomService.updateHousekeepingStatus(roomId, cleanStatus, isAvailable, assignedStaff);
        return roomRepository.findById(roomId).orElseThrow();
    }

    public Map<String, Long> getHousekeepingSummary() {
        List<Room> all = roomRepository.findAll();
        return all.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getHousekeepingStatus() != null ? r.getHousekeepingStatus() : "UNKNOWN",
                        Collectors.counting()
                ));
    }
}
