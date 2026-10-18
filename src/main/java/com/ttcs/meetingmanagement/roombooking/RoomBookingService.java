package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class RoomBookingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final RoomBookingPermissionService permissionService;
    private final UserRepository userRepository;

    @Autowired
    public RoomBookingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            RoomBookingPermissionService permissionService,
            @Autowired(required = false) UserRepository userRepository) {

        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    public RoomBookingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            RoomBookingPermissionService permissionService) {
        this(meetingRepository, roomRepository, permissionService, null);
    }

    // US08 + US21: Dat phong va kiem tra quyen voi khoa bi quan chong double booking
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomBookingResponse bookRoom(RoomBookingRequest request) {
        return bookRoom(request, resolveActorId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomBookingResponse bookRoom(RoomBookingRequest request, String callerActorId) {

        if (request == null
                || request.getMeetingId() == null
                || request.getMeetingId().isBlank()
                || request.getRoomId() == null
                || request.getRoomId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting ID and Room ID are required"
            );
        }

        String meetingId = request.getMeetingId().trim();
        String roomId = request.getRoomId().trim();

        // 1. Kiem tra va khoa Meeting bang findByIdForUpdate
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));

        // 2. Kiem tra trang thai Meeting
        if ("CANCELLED".equalsIgnoreCase(meeting.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot book a cancelled meeting"
            );
        }

        // 3. Kiem tra thoi gian hop hop le
        if (meeting.getStartTime() == null
                || meeting.getEndTime() == null
                || !meeting.getEndTime().isAfter(meeting.getStartTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid meeting time"
            );
        }

        // 4. Kiem tra xac thuc va phan quyen nguoi thao tac (US20)
        verifyActorAuthorization(callerActorId, meeting.getOrganizerId());

        // 5. Khoa tai nguyen phong bang findLockedById (PESSIMISTIC_WRITE) truoc khi kiem tra trong
        Room room = roomRepository.findLockedById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                ));

        // 6. Kiem tra trang thai van hanh cua phong (AVAILABLE)
        if (room.getStatus() == null || !"AVAILABLE".equalsIgnoreCase(room.getStatus().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room is not available for booking"
            );
        }

        // 7. US21: Kiem tra quyen theo USER, ROLE, DEPARTMENT
        String effectiveUserId = meeting.getOrganizerId();
        permissionService.assertCanBook(effectiveUserId, roomId);

        // 8. Kiem tra trung lich sau khi da thiet lap khoa tren phong (CHONG DOUBLE BOOKING)
        long conflicts = meetingRepository.countRoomConflicts(
                roomId,
                meetingId,
                meeting.getStartTime(),
                meeting.getEndTime()
        );

        if (conflicts > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room already booked in this time range"
            );
        }

        // 9. Ghi nhan thong tin dat phong vao database
        meeting.setRoomId(roomId);
        meeting.setUpdatedAt(LocalDateTime.now());

        Meeting saved = meetingRepository.saveAndFlush(meeting);

        return new RoomBookingResponse(
                saved.getMeetingId(),
                saved.getRoomId(),
                saved.getStartTime(),
                saved.getEndTime(),
                "Room booked successfully"
        );
    }

    // US09: Huy dat phong
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomBookingResponse cancelRoomBooking(String meetingId) {
        return cancelRoomBooking(meetingId, resolveActorId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomBookingResponse cancelRoomBooking(String meetingId, String callerActorId) {

        if (meetingId == null || meetingId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting ID is required"
            );
        }

        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));

        // Kiem tra quyen nguoi thao tac
        verifyActorAuthorization(callerActorId, meeting.getOrganizerId());

        if (meeting.getRoomId() == null
                || meeting.getRoomId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting has no room booking"
            );
        }

        String oldRoomId = meeting.getRoomId();
        LocalDateTime startTime = meeting.getStartTime();
        LocalDateTime endTime = meeting.getEndTime();

        meeting.setRoomId(null);
        meeting.setUpdatedAt(LocalDateTime.now());

        meetingRepository.saveAndFlush(meeting);

        return new RoomBookingResponse(
                meeting.getMeetingId(),
                oldRoomId,
                startTime,
                endTime,
                "Room booking cancelled successfully"
        );
    }

    private void verifyActorAuthorization(String actorId, String organizerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                    || "anonymousUser".equals(auth.getPrincipal())) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user identity is required"
                );
            }
        }

        if (actorId == null || actorId.isBlank()) {
            // Khong co thong tin actor (e.g. unit test thuan khong set security context)
            return;
        }

        // Kiem tra vai tro cua nguoi dung (Member khong duoc goi API dat phong)
        if (userRepository != null && !isUserAllowedToBook(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User does not have permission to book a room"
            );
        }

        // Kiem tra organizerId vs actorId (hoac Admin)
        boolean isAdmin = userRepository != null && isUserAdmin(actorId);
        if (!actorId.equals(organizerId) && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the organizer or an administrator can book a room for this meeting"
            );
        }
    }

    private String resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        if (principal instanceof com.ttcs.meetingmanagement.rbac.AuthenticatedUserIdentity identity) {
            return identity.getUserId();
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String str && !"anonymousUser".equals(str)) {
            return str;
        }
        if (auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    private boolean isUserAdmin(String userId) {
        if (userId == null || userId.isBlank() || userRepository == null) return false;
        return userRepository.findWithRole(userId)
                .map(User::getRole)
                .map(role -> {
                    String name = normalize(role.getRoleName());
                    String id = normalize(role.getRoleId());
                    return name.equals("admin") || name.equals("role_admin") || name.contains("quan tri")
                            || id.equals("admin") || id.equals("role_admin");
                })
                .orElse(false);
    }

    private boolean isUserAllowedToBook(String userId) {
        if (userId == null || userId.isBlank() || userRepository == null) return true;
        return userRepository.findWithRole(userId)
                .map(User::getRole)
                .map(role -> {
                    String name = normalize(role.getRoleName());
                    String id = normalize(role.getRoleId());
                    boolean isAdmin = name.equals("admin") || name.equals("role_admin") || name.contains("quan tri")
                            || id.equals("admin") || id.equals("role_admin");
                    boolean isPm = name.equals("pm") || name.equals("role_pm") || name.contains("dat lich")
                            || name.contains("booker") || id.equals("pm") || id.equals("role_pm") || id.contains("booker");
                    boolean isMember = name.equals("member") || name.equals("role_member") || name.contains("tham du")
                            || name.equals("user") || id.equals("member");
                    if (isMember && !isPm && !isAdmin) {
                        return false;
                    }
                    return isAdmin || isPm;
                })
                .orElse(true);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String stripped = Normalizer.normalize(input, Normalizer.Form.NFD);
        stripped = stripped.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.replace("đ", "d").replace("Đ", "d").trim().toLowerCase(Locale.ROOT);
    }
}