package com.ttcs.meetingmanagement.room;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public RoomResponse getRoomById(Long id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy phòng có id = " + id
                        )
                );

        return toResponse(room);
    }

    public List<RoomResponse> filterByParticipantCount(
            Integer participantCount
    ) {

        if (participantCount == null || participantCount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số người tham dự phải lớn hơn 0"
            );
        }

        return roomRepository
                .findByCapacityGreaterThanEqualOrderByCapacityAsc(
                        participantCount
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getStatus()
        );
    }
}