package com.ttcs.meetingmanagement.exception;

import org.springframework.http.HttpStatus;

public class RoomException extends RuntimeException {

    private final HttpStatus status;

    public RoomException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public RoomException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}