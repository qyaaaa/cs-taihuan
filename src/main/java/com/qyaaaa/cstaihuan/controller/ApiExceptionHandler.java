package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternalError(Exception ex) {
        log.error("Unhandled api error", ex);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", "服务器内部错误，请查看后端日志。");
        body.put("code", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
