package com.qyaaaa.cstaihuan.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buff")
@Getter
@Setter
public class BuffProperties {
    private String baseUrl = "https://buff.163.com";
    private String game = "csgo";
    private int pageSize = 80;
    private int timeoutMillis = 15000;
    private long fetchCooldownSeconds = 180;
    private CatalogSync catalogSync = new CatalogSync();

    public void setCatalogSync(CatalogSync catalogSync) {
        this.catalogSync = catalogSync == null ? new CatalogSync() : catalogSync;
    }

    @Data
    public static class CatalogSync {
        private long requestIntervalMillis = 5000L;
        private int maxDetailRequestsPerRun = 0;
        private long cacheFreshMillis = 3600000L;
    }
}
