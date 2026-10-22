
package com.ttcs.meetingmanagement.dto;

import java.time.Instant;

public record QrCodeResponse(
        String meetingId,
        String qrToken,
        Instant expiresAt,
        String qrImageBase64
) {
}
