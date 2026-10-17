package com.ttcs.meetingmanagement.auth;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserSummary user
) {
    public record UserSummary(
            String userId,
            String email,
            String fullName,
            String roleId,
            String roleName,
            String roleCategory,
            String status
    ) {
    }
}
