package com.qyaaaa.cstaihuan.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QrLoginStartResponse {
    private String sessionId;
    private String qrcode;
    private String status;
    private String message;
    private long expiresAt;
    // Null for a "pending" session (no account yet); the account is created on success.
    private Long accountId;

    public QrLoginStartResponse(String sessionId, String qrcode, String status, String message, long expiresAt) {
        this(sessionId, qrcode, status, message, expiresAt, null);
    }

    public QrLoginStartResponse(String sessionId, String qrcode, String status, String message, long expiresAt, Long accountId) {
        this.sessionId = sessionId;
        this.qrcode = qrcode;
        this.status = status;
        this.message = message;
        this.expiresAt = expiresAt;
        this.accountId = accountId;
    }
}
