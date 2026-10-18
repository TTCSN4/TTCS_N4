package com.ttcs.meetingmanagement.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================
    // RESPONSE STATUS EXCEPTION (US08 / US09 / US21 / US20)
    // =========================
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", ex.getStatusCode().value());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        response.put("error", status != null ? status.getReasonPhrase() : "Error");
        response.put("message", ex.getReason());

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(response);
    }

    // =========================
    // MEETING EXCEPTION (US01)
    // =========================
    @ExceptionHandler(MeetingException.class)
    public ResponseEntity<Map<String, Object>> handleMeetingException(
            MeetingException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", ex.getStatus().value());
        response.put("error", ex.getStatus().getReasonPhrase());
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(ex.getStatus())
                .body(response);
    }

    // =========================
    // SECURITY ACCESS DENIED (403)
    // =========================
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", "Forbidden");
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    // =========================
    // SECURITY AUTHENTICATION (401)
    // =========================
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", "Unauthorized");
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    // =========================
    // ROOM EXCEPTION
    // =========================
    @ExceptionHandler(RoomException.class)
    public ResponseEntity<Map<String, String>> handleRoomException(RoomException ex) {

        Map<String, String> response = new HashMap<>();
        response.put("message", ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    // =========================
    // EQUIPMENT EXCEPTION
    // =========================
    @ExceptionHandler(EquipmentException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentException(
            EquipmentException ex) {

        Map<String, Object> response = new HashMap<>();

        response.put("status", ex.getStatus().value());
        response.put("error", ex.getStatus().getReasonPhrase());
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(ex.getStatus())
                .body(response);
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

        Map<String, Object> response = new HashMap<>();

        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put(
                "message",
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

        Map<String, Object> response = new HashMap<>();

        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put(
                "message",
                "Dữ liệu không hợp lệ hoặc vi phạm ràng buộc cơ sở dữ liệu"
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // =========================
    // OTHER EXCEPTIONS
    // =========================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex) {

        Map<String, Object> response = new HashMap<>();

        response.put(
                "status",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        response.put(
                "error",
                "Internal Server Error"
        );

        response.put(
                "message",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}