package com.qyaaaa.cstaihuan.dto;

public class BuffSessionImportRequest {
    private String cookie;
    private String source = "frontend";

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

