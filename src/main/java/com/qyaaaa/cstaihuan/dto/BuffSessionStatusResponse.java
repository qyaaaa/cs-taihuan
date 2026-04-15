package com.qyaaaa.cstaihuan.dto;

public class BuffSessionStatusResponse {
    private boolean connected;
    private boolean valid;
    private String source;
    private String maskedCookie;
    private String updatedAt;
    private String lastValidatedAt;
    private String message;

    public BuffSessionStatusResponse() {
    }

    public BuffSessionStatusResponse(boolean connected, boolean valid, String source, String maskedCookie, String updatedAt, String lastValidatedAt, String message) {
        this.connected = connected;
        this.valid = valid;
        this.source = source;
        this.maskedCookie = maskedCookie;
        this.updatedAt = updatedAt;
        this.lastValidatedAt = lastValidatedAt;
        this.message = message;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMaskedCookie() {
        return maskedCookie;
    }

    public void setMaskedCookie(String maskedCookie) {
        this.maskedCookie = maskedCookie;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(String lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

