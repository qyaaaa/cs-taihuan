package com.qyaaaa.cstaihuan.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BuffSessionStatusResponse {
    private boolean connected;
    private boolean valid;
    private String source;
    private String maskedCookie;
    private String updatedAt;
    private String lastValidatedAt;
    private String message;
    // 规范账号 id：通常等于请求的账号；当导入命中已有 BUFF 账号并合并时，返回被合并到的那个账号 id，供前端切换。
    private Long accountId;

    public BuffSessionStatusResponse(boolean connected, boolean valid, String source, String maskedCookie, String updatedAt, String lastValidatedAt, String message) {
        this.connected = connected;
        this.valid = valid;
        this.source = source;
        this.maskedCookie = maskedCookie;
        this.updatedAt = updatedAt;
        this.lastValidatedAt = lastValidatedAt;
        this.message = message;
    }
}
