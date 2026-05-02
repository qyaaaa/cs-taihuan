package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BuffRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleBuffRateLimit(BuffRateLimitException ex) {
        log.warn("BUFF rate limit: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", ex.getMessage());
        body.put("code", "BUFF_RATE_LIMIT");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", ex.getMessage());
        body.put("code", "BAD_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.toList());
        String message = errors.isEmpty() ? ErrorMessages.VALIDATION_FAILED : String.join("; ", errors);
        log.warn("Validation failed: {}", message);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        body.put("code", "VALIDATION_ERROR");
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .collect(Collectors.toList());
        String message = errors.isEmpty() ? ErrorMessages.VALIDATION_FAILED : String.join("; ", errors);
        log.warn("Constraint validation failed: {}", message);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        body.put("code", "VALIDATION_ERROR");
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternalError(Exception ex) {
        log.error("Unhandled api error", ex);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", ErrorMessages.INTERNAL_API_ERROR);
        body.put("code", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
