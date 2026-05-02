package com.qyaaaa.cstaihuan.model;

import lombok.Data;

@Data
public class BuffAccount {
    private long id;
    private String nickname;
    private String buffUserId;
    private String maskedCookie;
    private String status;
    private String lastValidatedAt;
    private long createdAt;
    private long updatedAt;
}
