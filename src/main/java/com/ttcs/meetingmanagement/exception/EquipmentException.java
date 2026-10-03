package com.ttcs.meetingmanagement.exception;

import org.springframework.http.HttpStatus;

public class EquipmentException extends RuntimeException {

    private final HttpStatus status;

    public EquipmentException(
            String message,
            HttpStatus status) {

        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
