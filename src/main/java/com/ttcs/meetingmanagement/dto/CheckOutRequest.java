
package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckOutRequest(

        @NotBlank(message = "Meeting ID is required")
        String meetingId,

        @NotBlank(message = "User ID is required")
        String userId

) {
}
