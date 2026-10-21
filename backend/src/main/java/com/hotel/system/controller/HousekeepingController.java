package com.hotel.system.controller;

import com.hotel.system.model.Room;
import com.hotel.system.service.HousekeepingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/housekeeping")
@RequiredArgsConstructor
public class HousekeepingController {

    private final HousekeepingService housekeepingService;

    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getRooms(
            @RequestParam(required = false) String status) {
        if (status != null && !status.trim().isEmpty()) {
            return ResponseEntity.ok(housekeepingService.getRoomsByStatus(status));
        }
        return ResponseEntity.ok(housekeepingService.getAllRoomsForHousekeeping());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getSummary() {
        return ResponseEntity.ok(housekeepingService.getHousekeepingSummary());
    }

    @PutMapping("/{roomId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String roomId,
            @RequestBody Map<String, Object> updates) {
        try {
            String status = (String) updates.get("status");
            String assignedStaff = (String) updates.get("assignedStaff");
            Room updated = housekeepingService.updateRoomHousekeeping(roomId, status, assignedStaff);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
