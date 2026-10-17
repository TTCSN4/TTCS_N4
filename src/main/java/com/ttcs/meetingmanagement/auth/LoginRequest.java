package com.ttcs.meetingmanagement.auth;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LoginRequest(
        @JsonAlias({"identity", "username", "account"})
        String email,
        String password
) {
    public String getIdentifier() {
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "";
    }
}
