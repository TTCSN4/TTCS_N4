
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.repository.RoomRestrictionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomBookingPermissionService {

    private final RoomRestrictionRepository repository;

    public RoomBookingPermissionService(
            RoomRestrictionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public void assertCanBook(
            String userId,
            String roomId) {

        // 1. Kiem tra tham so
        if (userId == null || userId.isBlank()
                || roomId == null || roomId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID and Room ID are required"
            );
        }

        userId = userId.trim();
        roomId = roomId.trim();

        // 2. Kiem tra user
        if (!repository.userExists(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
            );
        }

        // 3. Kiem tra phong
        if (!repository.roomExists(roomId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        // 4. Phong khong gioi han
        if (!repository.hasRestrictions(roomId)) {
            return;
        }

        // 5. Kiem tra USER, ROLE, DEPARTMENT
        if (!repository.isAllowed(roomId, userId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to book this room"
            );
        }
    }
}
