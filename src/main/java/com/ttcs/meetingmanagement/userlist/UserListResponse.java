
package com.ttcs.meetingmanagement.userlist;

import java.time.LocalDateTime;
import java.util.List;

public record UserListResponse(

        List<UserSummary> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) {

    // Thong tin co ban cua nguoi dung
    public record UserSummary(

            String userId,

            String fullName,

            String email,

            String phone,

            String status,

            String roleId,

            String roleName,

            String departmentId,

            LocalDateTime createdAt

    ) {
    }
}
