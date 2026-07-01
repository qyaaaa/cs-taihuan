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
    // 无账号 pending 二维码会话登录成功时返回：后端创建账号并返回 id，前端据此切换账号。
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
