package com.ttcs.meetingmanagement.exception;

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("An authenticated user is required");
    }
}
