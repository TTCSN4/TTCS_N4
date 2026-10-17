package com.ttcs.meetingmanagement.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    // =========================
    // ROOM EXCEPTION
    // =========================
    @ExceptionHandler(RoomException.class)
    public ResponseEntity<Map<String, Object>> handleRoomException(RoomException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(buildErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    // =========================
    // EQUIPMENT EXCEPTION
    // =========================
    @ExceptionHandler(EquipmentException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentException(EquipmentException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(buildErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    // =========================
    // VALIDATION EXCEPTION
    // =========================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        Map<String, Object> response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                errors.values()
                        .stream()
                        .findFirst()
                        .orElse("Dữ liệu không hợp lệ")
        );
        response.put("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // =========================
    // DATABASE CONSTRAINT ERROR
    // =========================
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        String detail = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(
                        HttpStatus.CONFLICT,
                        "Dữ liệu không hợp lệ hoặc vi phạm ràng buộc cơ sở dữ liệu: " + detail
                ));
    }

    // =========================
    // OTHER EXCEPTIONS
    // =========================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ex.getMessage()
                ));
    }
}