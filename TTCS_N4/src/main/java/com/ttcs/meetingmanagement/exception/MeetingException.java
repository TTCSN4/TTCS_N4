package com.ttcs.meetingmanagement.exception;

import org.springframework.http.HttpStatus;

public class MeetingException extends RuntimeException {

    private final HttpStatus status;

    public MeetingException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}