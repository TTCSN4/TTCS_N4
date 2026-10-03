package com.ttcs.meetingmanagement.exception;

public class MeetingForbiddenException extends RuntimeException {

    public MeetingForbiddenException() {
        super("Only the meeting organizer can modify or cancel this meeting");
    }
}
