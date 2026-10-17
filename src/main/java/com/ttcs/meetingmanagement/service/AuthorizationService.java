package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;

    public AuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Xác thực quyền quản lý lịch họp (Tạo, Sửa, Hủy).
     * Chỉ ADMIN và PROJECT_MANAGER mới có quyền thực hiện.
     * Ném ResponseStatusException(HttpStatus.FORBIDDEN) nếu không đủ quyền.
     */
    public void validateMeetingManagementPermission(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng cung cấp mã người dùng (User ID) để xác thực quyền."
            );
        }

        // Hỗ trợ ánh xạ tương thích nếu client gửi ID là 1
        String normalizedId = "1".equals(userId.trim()) ? "USER001" : userId.trim();

        User user = userRepository.findById(normalizedId)
                .or(() -> userRepository.findByEmail(normalizedId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng với mã: " + userId
                ));

        if (!hasMeetingManagementRole(user)) {
            String roleName = user.getRole() != null ? user.getRole().getRoleName() : "USER";
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này. Quyền hiện tại: " + roleName +
                            ". Chỉ có Admin và Project Manager mới được tạo, sửa hoặc hủy lịch họp."
            );
        }
    }

    /**
     * Xác thực quyền quản lý lịch họp dùng riêng cho MeetingService (ném MeetingException).
     */
    public void validateMeetingManagementForService(String userId) {
        try {
            validateMeetingManagementPermission(userId);
        } catch (ResponseStatusException ex) {
            throw new MeetingException(ex.getReason(), HttpStatus.valueOf(ex.getStatusCode().value()));
        }
    }

    public boolean hasMeetingManagementRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String roleName = user.getRole().getRoleName() != null ? user.getRole().getRoleName().trim().toUpperCase() : "";
        String roleId = user.getRole().getRoleId() != null ? user.getRole().getRoleId().trim().toUpperCase() : "";

        return isManagementRole(roleName) || isManagementRole(roleId);
    }

    private boolean isManagementRole(String value) {
        return value.equals("ADMIN")
                || value.equals("ROLE_ADMIN")
                || value.equals("PROJECT_MANAGER")
                || value.equals("PROJECT MANAGER")
                || value.equals("ROLE_PM")
                || value.equals("ROLE_PROJECT_MANAGER")
                || value.equals("PM");
    }
}
