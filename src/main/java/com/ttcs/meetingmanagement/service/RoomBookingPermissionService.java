package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoomRestrictionRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.security.BackendActor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class RoomBookingPermissionService {

    private final RoomRestrictionRepository repository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final MeetingRepository meetingRepository;

    public RoomBookingPermissionService(
            RoomRestrictionRepository repository,
            UserRepository userRepository,
            RoomRepository roomRepository,
            MeetingRepository meetingRepository) {

        this.repository = repository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.meetingRepository = meetingRepository;
    }

    // Xac thuc nguoi dung hien tai tu Backend Security Context
    public String resolveCurrentUserId() {
        return BackendActor.requireUserId();
    }

    // Kiem tra vai tro: Chi Admin va PM co quyen quan ly dat phong, Member bi 403
    @Transactional(readOnly = true)
    public User verifyUserRole(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID is required"
            );
        }

        User user = userRepository.findWithRole(userId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        if (!isAdminOrPm(user)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only Admin and Project Manager have permission to manage room bookings"
            );
        }

        return user;
    }

    public boolean isAdminOrPm(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        Role role = user.getRole();
        String roleName = role.getRoleName() != null ? role.getRoleName().trim().toUpperCase(Locale.ROOT) : "";
        String roleId = role.getRoleId() != null ? role.getRoleId().trim().toUpperCase(Locale.ROOT) : "";

        boolean isAdmin = roleName.equals("ADMIN") || roleName.equals("ROLE_ADMIN")
                || roleId.equals("ADMIN") || roleId.equals("ROLE_ADMIN");

        boolean isPm = roleName.equals("PM") || roleName.equals("ROLE_PM")
                || roleName.equals("PROJECT_MANAGER") || roleName.equals("PROJECT MANAGER")
                || roleName.equals("NGƯỜI ĐẶT LỊCH")
                || roleId.equals("PM") || roleId.equals("ROLE_PM")
                || roleId.equals("PROJECT_MANAGER");

        return isAdmin || isPm;
    }

    // ==========================================
    // 1. KIEM TRA QUYEN DAT PHONG
    // ==========================================
    @Transactional(readOnly = true)
    public void assertCanBook(
            String userId,
            String roomId) {

        if (userId == null || userId.isBlank()
                || roomId == null || roomId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID and Room ID are required"
            );
        }

        userId = userId.trim();
        roomId = roomId.trim();

        // 1. Kiem tra user va vai tro (Admin/PM duoc phep, Member bi 403)
        verifyUserRole(userId);

        // 2. Kiem tra phong ton tai
        if (!roomRepository.existsById(roomId) && !repository.roomExists(roomId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        // 3. Phong khong gioi han
        if (!repository.hasRestrictions(roomId)) {
            return;
        }

        // 4. Kiem tra USER, ROLE, DEPARTMENT theo room_restriction
        if (!repository.isAllowed(roomId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to book this room"
            );
        }
    }

    @Transactional(readOnly = true)
    public void assertCanBook(String roomId) {
        String actorId = resolveCurrentUserId();
        assertCanBook(actorId, roomId);
    }

    // ==========================================
    // 2. KIEM TRA QUYEN CHINH SUA DAT PHONG
    // ==========================================
    @Transactional(readOnly = true)
    public void assertCanUpdate(
            String userId,
            String roomId) {

        assertCanBook(userId, roomId);
    }

    @Transactional(readOnly = true)
    public void assertCanUpdate(String roomId) {
        String actorId = resolveCurrentUserId();
        assertCanUpdate(actorId, roomId);
    }

    // ==========================================
    // 3. KIEM TRA QUYEN HUY DAT PHONG
    // ==========================================
    @Transactional(readOnly = true)
    public void assertCanCancel(
            String userId,
            String meetingId) {

        if (userId == null || userId.isBlank()
                || meetingId == null || meetingId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID and Meeting ID are required"
            );
        }

        userId = userId.trim();
        meetingId = meetingId.trim();

        // 1. Kiem tra user va vai tro (Admin/PM duoc phep, Member bi 403)
        verifyUserRole(userId);

        // 2. Kiem tra meeting ton tai
        if (!meetingRepository.existsById(meetingId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Meeting not found"
            );
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCancel(String meetingId) {
        String actorId = resolveCurrentUserId();
        assertCanCancel(actorId, meetingId);
    }
}
