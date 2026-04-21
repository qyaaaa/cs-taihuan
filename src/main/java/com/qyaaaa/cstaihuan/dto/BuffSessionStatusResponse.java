package com.qyaaaa.cstaihuan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuffSessionStatusResponse {
    private boolean connected;
    private boolean valid;
    private String source;
    private String maskedCookie;
    private String updatedAt;
    private String lastValidatedAt;
    private String message;
}
