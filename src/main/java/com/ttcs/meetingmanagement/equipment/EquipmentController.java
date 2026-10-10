
package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;

import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@CrossOrigin(origins = "*")
public class EquipmentController {

    private static final ZoneId VN_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final EquipmentService equipmentService;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentBookingRepository bookingRepository;
    private final MeetingRepository meetingRepository;

    public EquipmentController(
            EquipmentService equipmentService,
            EquipmentRepository equipmentRepository,
            EquipmentBookingRepository bookingRepository,
            MeetingRepository meetingRepository) {

        this.equipmentService = equipmentService;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.meetingRepository = meetingRepository;
    }

    // =========================================
    // 1. LAY DANH SACH THIET BI
    // =========================================

    @GetMapping
    public ResponseEntity<List<Equipment>> getAllEquipment() {

        return ResponseEntity.ok(
                equipmentService.getAllEquipment()
        );
    }

    // =========================================
    // 2. LAY CHI TIET THIET BI
    // =========================================

    @GetMapping("/{id}")
    public ResponseEntity<Equipment> getEquipmentById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.getEquipmentById(id)
        );
    }

    // =========================================
    // 3. THEM THIET BI
    // =========================================

    @PostMapping
    public ResponseEntity<Equipment> createEquipment(
            @Valid @RequestBody CreateEquipmentRequest request) {

        Equipment equipment =
                equipmentService.createEquipment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(equipment);
    }

    // =========================================
    // 4. CAP NHAT THIET BI
    // =========================================

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> updateEquipment(
            @PathVariable String id,
            @Valid @RequestBody UpdateEquipmentRequest request) {

        return ResponseEntity.ok(
                equipmentService.updateEquipment(id, request)
        );
    }

    // =========================================
    // 5. XOA THIET BI
    // =========================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable String id) {

        equipmentService.deleteEquipment(id);

        return ResponseEntity.noContent().build();
    }

    // =========================================
    // 6. KIEM TRA THIET BI DA DUOC DAT
    // =========================================

    @GetMapping("/{id}/booked")
    public ResponseEntity<Boolean> checkBooked(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.isBooked(id)
        );
    }

    // =========================================
    // 7. KIEM TRA TINH TRANG THIET BI
    // =========================================

    @GetMapping("/status")
    @Transactional(readOnly = true)
    public List<EquipmentStatusResponse> status(
            @RequestParam String room,
            @RequestParam OffsetDateTime at) {

        if (room == null || room.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room is required"
            );
        }

        return equipmentRepository
                .findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(
                        room.trim()
                )
                .stream()
                .map(equipment -> statusAt(equipment, at))
                .toList();
    }

    // =========================================
    // 8. DAT THIET BI
    // =========================================

    @PostMapping("/bookings")
    @Transactional
    public BookingResponse book(
            @RequestBody EquipmentBookingRequest request) {

        return createReservation(
                request,
                EquipmentBookingStatus.BOOKED
        );
    }

    // =========================================
    // 9. DAT LICH BAO TRI
    // =========================================

    @PostMapping("/maintenance")
    @Transactional
    public BookingResponse scheduleMaintenance(
            @RequestBody EquipmentBookingRequest request) {

        return createReservation(
                request,
                EquipmentBookingStatus.MAINTENANCE
        );
    }

    // =========================================
    // 10. HUY DAT THIET BI
    // =========================================

    @DeleteMapping("/bookings/{id}")
    @Transactional
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long id) {

        EquipmentBooking booking =
                bookingRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Equipment booking not found"
                                )
                        );

        booking.setStatus(
                EquipmentBookingStatus.CANCELLED
        );

        bookingRepository.save(booking);

        return ResponseEntity.noContent().build();
    }

    // =========================================
    // 11. TAO LUOT DAT THIET BI
    // =========================================

    private BookingResponse createReservation(
            EquipmentBookingRequest request,
            EquipmentBookingStatus status) {

        validateReservation(request);

        Equipment equipment =
                equipmentRepository.findByIdForUpdate(
                        request.equipmentId()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Equipment not found"
                        )
                );

        String room = request.room().trim();

        if (equipment.getRoomId() == null
                || !equipment.getRoomId()
                        .equalsIgnoreCase(room)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Equipment does not belong to this room"
            );
        }

        String meetingId = request.meetingId();

        if (meetingId != null) {
            if (meetingId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Meeting ID cannot be blank"
                );
            }

            meetingId = meetingId.trim();

            validateMeeting(request, meetingId, room);
        }

        ensureCapacity(equipment, request, status);

        EquipmentBooking reservation =
                new EquipmentBooking();

        reservation.setEquipment(equipment);
        reservation.setMeetingId(meetingId);
        reservation.setRoom(room);
        reservation.setQuantity(request.quantity());
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(status);

        return toResponse(
                bookingRepository.save(reservation)
        );
    }

    // =========================================
    // 12. KIEM TRA DU LIEU DAT THIET BI
    // =========================================

    private void validateReservation(
            EquipmentBookingRequest request) {

        if (request == null
                || request.equipmentId() == null
                || request.equipmentId().isBlank()
                || request.room() == null
                || request.room().isBlank()
                || request.quantity() == null
                || request.quantity() < 1
                || request.startTime() == null
                || request.endTime() == null
                || !request.endTime()
                        .isAfter(request.startTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid equipment reservation"
            );
        }
    }

    // =========================================
    // 13. KIEM TRA CUOC HOP
    // =========================================

    private void validateMeeting(
            EquipmentBookingRequest request,
            String meetingId,
            String room) {

        // Meeting ID trong ERD la String
        Meeting meeting =
                meetingRepository.findById(meetingId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Meeting not found"
                                )
                        );

        // Status trong Meeting la String
        if (!"SCHEDULED".equalsIgnoreCase(
                meeting.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting is not scheduled"
            );
        }

        if (meeting.getStartTime() == null
                || meeting.getEndTime() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting time is invalid"
            );
        }

        // Meeting: LocalDateTime
        // EquipmentBooking: OffsetDateTime
        // Quy doi gio hop sang mui gio Viet Nam

        OffsetDateTime meetingStart =
                meeting.getStartTime()
                        .atZone(VN_ZONE)
                        .toOffsetDateTime();

        OffsetDateTime meetingEnd =
                meeting.getEndTime()
                        .atZone(VN_ZONE)
                        .toOffsetDateTime();

        if (request.startTime().isBefore(meetingStart)
                || request.endTime().isAfter(meetingEnd)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Equipment booking must be within meeting time"
            );
        }

        // getRoomId() thay cho getRoom()
        if (meeting.getRoomId() == null
                || !meeting.getRoomId()
                        .equalsIgnoreCase(room)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Equipment room does not match meeting room"
            );
        }
    }

    // =========================================
    // 14. KIEM TRA SO LUONG THIET BI
    // =========================================

    private void ensureCapacity(
            Equipment equipment,
            EquipmentBookingRequest request,
            EquipmentBookingStatus requestedStatus) {

        List<UsageEvent> events = new ArrayList<>();

        for (EquipmentBooking existing :
                bookingRepository.findAllByEquipment_EquipmentId(
                        equipment.getEquipmentId()
                )) {

            if (existing.getStatus()
                    == EquipmentBookingStatus.CANCELLED) {
                continue;
            }

            if (!overlaps(
                    existing.getStartTime(),
                    existing.getEndTime(),
                    request.startTime(),
                    request.endTime())) {
                continue;
            }

            addEvents(
                    events,
                    existing.getStartTime(),
                    existing.getEndTime(),
                    existing.getQuantity(),
                    request.startTime(),
                    request.endTime()
            );
        }

        addEvents(
                events,
                request.startTime(),
                request.endTime(),
                request.quantity(),
                request.startTime(),
                request.endTime()
        );

        events.sort(
                Comparator.comparing(UsageEvent::time)
                        .thenComparingInt(UsageEvent::change)
        );

        int inUse = 0;
        int peak = 0;

        for (UsageEvent event : events) {
            inUse += event.change();
            peak = Math.max(peak, inUse);
        }

        int totalQuantity =
                equipment.getTotalQuantity() == null
                        ? 0
                        : equipment.getTotalQuantity();

        if (peak > totalQuantity) {

            String message =
                    requestedStatus
                            == EquipmentBookingStatus.MAINTENANCE
                            ? "Not enough equipment for maintenance"
                            : "Not enough equipment available";

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    message
            );
        }
    }

    // =========================================
    // 15. THEM SU KIEN SU DUNG THIET BI
    // =========================================

    private void addEvents(
            List<UsageEvent> events,
            OffsetDateTime start,
            OffsetDateTime end,
            int quantity,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd) {

        OffsetDateTime clippedStart =
                start.isBefore(rangeStart)
                        ? rangeStart
                        : start;

        OffsetDateTime clippedEnd =
                end.isAfter(rangeEnd)
                        ? rangeEnd
                        : end;

        events.add(
                new UsageEvent(clippedStart, quantity)
        );

        events.add(
                new UsageEvent(clippedEnd, -quantity)
        );
    }

    // =========================================
    // 16. KIEM TRA TRUNG THOI GIAN
    // =========================================

    private boolean overlaps(
            OffsetDateTime firstStart,
            OffsetDateTime firstEnd,
            OffsetDateTime secondStart,
            OffsetDateTime secondEnd) {

        return firstStart.isBefore(secondEnd)
                && firstEnd.isAfter(secondStart);
    }

    // =========================================
    // 17. TRANG THAI THIET BI TAI THOI DIEM
    // =========================================

    private EquipmentStatusResponse statusAt(
            Equipment equipment,
            OffsetDateTime at) {

        int booked = 0;
        int maintenance = 0;

        for (EquipmentBooking reservation :
                bookingRepository.findAllByEquipment_EquipmentId(
                        equipment.getEquipmentId()
                )) {

            if (!reservation.getStartTime().isAfter(at)
                    && reservation.getEndTime().isAfter(at)) {

                if (reservation.getStatus()
                        == EquipmentBookingStatus.BOOKED) {

                    booked += reservation.getQuantity();
                }

                if (reservation.getStatus()
                        == EquipmentBookingStatus.MAINTENANCE) {

                    maintenance += reservation.getQuantity();
                }
            }
        }

        int totalQuantity =
                equipment.getTotalQuantity() == null
                        ? 0
                        : equipment.getTotalQuantity();

        int available = Math.max(
                0,
                totalQuantity - booked - maintenance
        );

        List<EquipmentAvailabilityStatus> statuses =
                new ArrayList<>();

        if (available > 0) {
            statuses.add(
                    EquipmentAvailabilityStatus.AVAILABLE
            );
        }

        if (booked > 0) {
            statuses.add(
                    EquipmentAvailabilityStatus.BOOKED
            );
        }

        if (maintenance > 0) {
            statuses.add(
                    EquipmentAvailabilityStatus.MAINTENANCE
            );
        }

        return new EquipmentStatusResponse(
                equipment.getEquipmentId(),
                equipment.getEquipmentName(),
                equipment.getRoomId(),
                totalQuantity,
                booked,
                maintenance,
                available,
                statuses
        );
    }

    // =========================================
    // 18. CHUYEN ENTITY SANG RESPONSE
    // =========================================

    private BookingResponse toResponse(
            EquipmentBooking reservation) {

        return new BookingResponse(
                reservation.getId(),
                reservation.getEquipment().getEquipmentId(),
                reservation.getMeetingId(),
                reservation.getRoom(),
                reservation.getQuantity(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus()
        );
    }

    // =========================================
    // 19. DTO VA RECORD
    // =========================================

    private record UsageEvent(
            OffsetDateTime time,
            int change
    ) {
    }

    public record EquipmentBookingRequest(
            String equipmentId,
            String meetingId,
            String room,
            Integer quantity,
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
    }

    public record BookingResponse(
            Long id,
            String equipmentId,
            String meetingId,
            String room,
            int quantity,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            EquipmentBookingStatus status
    ) {
    }

    public record EquipmentStatusResponse(
            String equipmentId,
            String name,
            String room,
            int totalQuantity,
            int bookedQuantity,
            int maintenanceQuantity,
            int availableQuantity,
            List<EquipmentAvailabilityStatus> statuses
    ) {
    }
}
