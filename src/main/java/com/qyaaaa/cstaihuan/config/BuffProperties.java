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
    private QrLogin qrLogin = new QrLogin();

    public void setCatalogSync(CatalogSync catalogSync) {
        this.catalogSync = catalogSync == null ? new CatalogSync() : catalogSync;
    }

    public void setQrLogin(QrLogin qrLogin) {
        this.qrLogin = qrLogin == null ? new QrLogin() : qrLogin;
    }

    @Data
    public static class CatalogSync {
        private long requestIntervalMillis = 5000L;
        private int maxDetailRequestsPerRun = 0;
        private long cacheFreshMillis = 3600000L;
        private boolean scheduledEnabled = true;
        private long scheduledInitialDelayMillis = 60000L;
        private long scheduledFixedDelayMillis = 900000L;
        private int scheduledMaxDetailRequestsPerRun = 30;
    }

    @Data
    public static class QrLogin {
        private boolean qrcodeEnabled = true;
        private long qrcodeTimeoutSeconds = 300L;
        private long qrcodePollIntervalMillis = 2000L;
        private boolean browserHeadless = true;
    }
}
