package com.qyaaaa.cstaihuan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buff")
public class BuffProperties {
    private String baseUrl = "https://buff.163.com";
    private String game = "csgo";
    private int pageSize = 80;
    private int timeoutMillis = 15000;
    private long fetchCooldownSeconds = 180;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public long getFetchCooldownSeconds() {
        return fetchCooldownSeconds;
    }

    public void setFetchCooldownSeconds(long fetchCooldownSeconds) {
        this.fetchCooldownSeconds = fetchCooldownSeconds;
    }
}
