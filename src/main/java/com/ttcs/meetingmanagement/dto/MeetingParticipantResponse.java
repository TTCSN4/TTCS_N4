
package com.ttcs.meetingmanagement.dto;

import com.ttcs.meetingmanagement.model.MeetingParticipant;
import java.time.LocalDateTime;

public record MeetingParticipantResponse(
        String participantId,
        String meetingId,
        String userId,
        String status,
        LocalDateTime respondedAt
) {

    public static MeetingParticipantResponse from(
            MeetingParticipant participant
    ) {
        return new MeetingParticipantResponse(
                participant.getParticipantId(),
                participant.getMeetingId(),
                participant.getUserId(),
                participant.getStatus(),
                participant.getRespondedAt()
        );
    }
}
