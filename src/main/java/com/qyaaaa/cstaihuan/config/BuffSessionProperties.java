package com.qyaaaa.cstaihuan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buff.session")
public class BuffSessionProperties {
    private String storagePath = "data/buff-session.json";

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
}

