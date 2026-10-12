
package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(

        @NotBlank(message = "QR token is required")
        String qrToken,

        @NotBlank(message = "User ID is required")
        String userId

) {
}
