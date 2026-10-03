package com.ttcs.meetingmanagement.exception;

public class MeetingNotFoundException extends RuntimeException {

    public MeetingNotFoundException(Long meetingId) {
        super("Meeting " + meetingId + " was not found");
    }
}
