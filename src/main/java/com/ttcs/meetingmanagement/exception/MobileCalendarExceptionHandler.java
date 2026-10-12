
package com.ttcs.meetingmanagement.exception;

import com.ttcs.meetingmanagement.controller.MobileMeetingCalendarController;
import com.ttcs.meetingmanagement.dto.MobileApiErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice(
        assignableTypes = MobileMeetingCalendarController.class
)
public class MobileCalendarExceptionHandler {

    // CUOC HOP KHONG TON TAI HOAC KHONG CO QUYEN
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<MobileApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                ex.getMessage()
        );
    }

    // THAM SO KHONG HOP LE
    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<MobileApiErrorResponse> handleBadRequest(
            Exception ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                ex.getMessage()
        );
    }

    private ResponseEntity<MobileApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(
                        new MobileApiErrorResponse(
                                code,
                                message,
                                Instant.now()
                        )
                );
    }
}
