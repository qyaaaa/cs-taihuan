package com.qyaaaa.cstaihuan.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QrLoginStatusResponse {
    private String sessionId;
    private String status;
    private String message;
    private boolean connected;
    private boolean valid;
    // Set when a login succeeds for a "pending" QR session that had no account yet:
    // the backend creates the account and returns its id so the frontend can switch to it.
    private Long accountId;

    public QrLoginStatusResponse(String sessionId, String status, String message, boolean connected, boolean valid) {
        this(sessionId, status, message, connected, valid, null);
    }

    public QrLoginStatusResponse(String sessionId, String status, String message, boolean connected, boolean valid, Long accountId) {
        this.sessionId = sessionId;
        this.status = status;
        this.message = message;
        this.connected = connected;
        this.valid = valid;
        this.accountId = accountId;
    }
}
