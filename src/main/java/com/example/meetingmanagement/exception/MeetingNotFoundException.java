package com.example.meetingmanagement.exception;

public class MeetingNotFoundException extends RuntimeException {

    public MeetingNotFoundException(Object meetingId) {
        super("Meeting " + meetingId + " was not found");
    }
}
