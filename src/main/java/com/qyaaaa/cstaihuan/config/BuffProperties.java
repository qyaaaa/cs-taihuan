package com.qyaaaa.cstaihuan.config;

import java.util.LinkedHashMap;
import java.util.Map;
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
    // 武库通行证（armory）等“发放渠道”会把不同箱子的皮肤混成一个收藏品。这里把 goods detail 的 containers
    // 标识（如 fever case / set_train_2025）映射成真实中文收藏品名，键统一用小写。每出新箱补一条。
    private Map<String, String> collectionNameMapping = new LinkedHashMap<String, String>();

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
