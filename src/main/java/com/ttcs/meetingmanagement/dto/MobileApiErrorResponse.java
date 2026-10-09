
package com.ttcs.meetingmanagement.dto;

import java.time.Instant;

public record MobileApiErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}
