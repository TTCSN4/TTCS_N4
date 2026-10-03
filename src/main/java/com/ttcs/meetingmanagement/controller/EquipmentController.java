package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.model.Equipment;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.EquipmentRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {
    private final EquipmentRepository equipmentRepository;
    private final EquipmentBookingRepository bookingRepository;
    private final MeetingRepository meetingRepository;

    public EquipmentController(
            EquipmentRepository equipmentRepository,
            EquipmentBookingRepository bookingRepository,
            MeetingRepository meetingRepository
    ) {
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.meetingRepository = meetingRepository;
    }

    @GetMapping
    public List<EquipmentResponse> list(@RequestParam(required = false) String room) {
        List<Equipment> equipment = room == null || room.isBlank()
                ? equipmentRepository.findAll()
                : equipmentRepository.findByRoomIgnoreCaseOrderByNameAsc(room.trim());
        return equipment.stream().map(this::toResponse).toList();
    }

    @PostMapping
    public EquipmentResponse create(@RequestBody EquipmentRequest request) {
        if (request.name() == null || request.name().isBlank()
                || request.room() == null || request.room().isBlank()
                || request.quantity() == null || request.quantity() < 1) {
            throw badRequest("Tên, phòng và số lượng thiết bị hợp lệ là bắt buộc.");
        }
        Equipment equipment = new Equipment();
        equipment.setName(request.name().trim());
        equipment.setRoom(request.room().trim());
        equipment.setTotalQuantity(request.quantity());
        return toResponse(equipmentRepository.save(equipment));
    }

    @GetMapping("/status")
    @Transactional(readOnly = true)
    public List<EquipmentStatusResponse> status(
            @RequestParam String room,
            @RequestParam OffsetDateTime at
    ) {
        if (room.isBlank()) throw badRequest("Vui lòng cung cấp phòng cần tra cứu.");
        return equipmentRepository.findByRoomIgnoreCaseOrderByNameAsc(room.trim()).stream()
                .map(item -> statusAt(item, at))
                .toList();
    }

    @PostMapping("/maintenance")
    @Transactional
    public BookingResponse scheduleMaintenance(@RequestBody EquipmentBookingRequest request) {
        return createReservation(request, EquipmentBookingStatus.MAINTENANCE);
    }

    private BookingResponse createReservation(EquipmentBookingRequest request, EquipmentBookingStatus status) {
        validateReservation(request);
        Equipment equipment = equipmentRepository.findByIdForUpdate(request.equipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị."));
        String room = request.room().trim();
        if (!equipment.getRoom().equalsIgnoreCase(room)) {
            throw badRequest("Thiết bị không thuộc phòng được yêu cầu.");
        }

        if (request.meetingId() != null) {
            validateMeeting(request, room);
        }
        ensureCapacity(equipment, request, status);

        EquipmentBooking reservation = new EquipmentBooking();
        reservation.setEquipment(equipment);
        reservation.setMeetingId(request.meetingId());
        reservation.setRoom(room);
        reservation.setQuantity(request.quantity());
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(status);
        return toResponse(bookingRepository.save(reservation));
    }

    private void validateReservation(EquipmentBookingRequest request) {
        if (request.equipmentId() == null || request.room() == null || request.room().isBlank()
                || request.quantity() == null || request.quantity() < 1
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            throw badRequest("Thiết bị, phòng, số lượng và khoảng thời gian hợp lệ là bắt buộc.");
        }
    }

    private void validateMeeting(EquipmentBookingRequest request, String room) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc họp."));
        if (meeting.getStatus() != MeetingStatus.SCHEDULED
                || request.startTime().isBefore(meeting.getStartTime())
                || request.endTime().isAfter(meeting.getEndTime())) {
            throw badRequest("Thời gian đặt thiết bị phải nằm trong cuộc họp đang diễn ra theo lịch.");
        }
        if (meeting.getRoom() != null && !meeting.getRoom().equalsIgnoreCase(room)) {
            throw badRequest("Phòng đặt thiết bị không trùng với phòng cuộc họp.");
        }
    }

    private void ensureCapacity(Equipment equipment, EquipmentBookingRequest request, EquipmentBookingStatus requestedStatus) {
        List<UsageEvent> events = new ArrayList<>();
        for (EquipmentBooking existing : bookingRepository.findAllByEquipment_Id(equipment.getId())) {
            if (existing.getStatus() == EquipmentBookingStatus.CANCELLED
                    || !overlaps(existing.getStartTime(), existing.getEndTime(), request.startTime(), request.endTime())) {
                continue;
            }
            addEvents(events, existing.getStartTime(), existing.getEndTime(), existing.getQuantity(), request.startTime(), request.endTime());
        }
        addEvents(events, request.startTime(), request.endTime(), request.quantity(), request.startTime(), request.endTime());

        int inUse = 0;
        int peak = 0;
        events.sort(Comparator.comparing(UsageEvent::time).thenComparingInt(UsageEvent::change));
        for (UsageEvent event : events) {
            inUse += event.change();
            peak = Math.max(peak, inUse);
        }
        if (peak > equipment.getTotalQuantity()) {
            String message = requestedStatus == EquipmentBookingStatus.MAINTENANCE
                    ? "Số lượng thiết bị bảo trì vượt quá số lượng còn khả dụng trong khung giờ này."
                    : "Không đủ thiết bị khả dụng trong khung giờ này.";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void addEvents(
            List<UsageEvent> events,
            OffsetDateTime start,
            OffsetDateTime end,
            int quantity,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd
    ) {
        OffsetDateTime clippedStart = start.isBefore(rangeStart) ? rangeStart : start;
        OffsetDateTime clippedEnd = end.isAfter(rangeEnd) ? rangeEnd : end;
        events.add(new UsageEvent(clippedStart, quantity));
        events.add(new UsageEvent(clippedEnd, -quantity));
    }

    private boolean overlaps(OffsetDateTime firstStart, OffsetDateTime firstEnd, OffsetDateTime secondStart, OffsetDateTime secondEnd) {
        return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
    }

    private EquipmentStatusResponse statusAt(Equipment equipment, OffsetDateTime at) {
        int booked = 0;
        int maintenance = 0;
        for (EquipmentBooking reservation : bookingRepository.findAllByEquipment_Id(equipment.getId())) {
            if (!reservation.getStartTime().isAfter(at) && reservation.getEndTime().isAfter(at)) {
                if (reservation.getStatus() == EquipmentBookingStatus.BOOKED) booked += reservation.getQuantity();
                if (reservation.getStatus() == EquipmentBookingStatus.MAINTENANCE) maintenance += reservation.getQuantity();
            }
        }
        int available = Math.max(0, equipment.getTotalQuantity() - booked - maintenance);
        List<EquipmentAvailabilityStatus> statuses = new ArrayList<>();
        if (available > 0) statuses.add(EquipmentAvailabilityStatus.AVAILABLE);
        if (booked > 0) statuses.add(EquipmentAvailabilityStatus.BOOKED);
        if (maintenance > 0) statuses.add(EquipmentAvailabilityStatus.MAINTENANCE);
        return new EquipmentStatusResponse(
                equipment.getId(), equipment.getName(), equipment.getRoom(), equipment.getTotalQuantity(),
                booked, maintenance, available, statuses
        );
    }

    private EquipmentResponse toResponse(Equipment equipment) {
        return new EquipmentResponse(equipment.getId(), equipment.getName(), equipment.getRoom(), equipment.getTotalQuantity());
    }

    private BookingResponse toResponse(EquipmentBooking reservation) {
        return new BookingResponse(
                reservation.getId(), reservation.getEquipment().getId(), reservation.getMeetingId(), reservation.getRoom(),
                reservation.getQuantity(), reservation.getStartTime(), reservation.getEndTime(), reservation.getStatus()
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record UsageEvent(OffsetDateTime time, int change) { }

    public record EquipmentRequest(String name, String room, Integer quantity) { }

    public record EquipmentBookingRequest(
            Long equipmentId,
            Long meetingId,
            String room,
            Integer quantity,
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) { }

    public record EquipmentResponse(Long id, String name, String room, int totalQuantity) { }

    public record BookingResponse(
            Long id,
            Long equipmentId,
            Long meetingId,
            String room,
            int quantity,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            EquipmentBookingStatus status
    ) { }

    public record EquipmentStatusResponse(
            Long equipmentId,
            String name,
            String room,
            int totalQuantity,
            int bookedQuantity,
            int maintenanceQuantity,
            int availableQuantity,
            List<EquipmentAvailabilityStatus> statuses
    ) { }
}