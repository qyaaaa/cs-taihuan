package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BuffRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleBuffRateLimit(BuffRateLimitException ex) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", ex.getMessage());
        body.put("code", "BUFF_RATE_LIMIT");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", ex.getMessage());
        body.put("code", "BAD_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
