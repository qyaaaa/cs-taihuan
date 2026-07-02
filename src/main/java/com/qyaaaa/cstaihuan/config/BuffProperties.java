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
    private FloatRefine floatRefine = new FloatRefine();
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

    // 库存拉取后自动“按磨损精估材料价”的配置：每件一个 BUFF 挂单请求，必须限额防限流。
    @Data
    public static class FloatRefine {
        /** 拉取库存后是否自动精估（仅异步任务路径；结转上一快照精估价始终执行）。 */
        private boolean autoRefineOnFetch = true;
        /** 后台精估每轮最多发起的挂单查询次数（按 goods+磨损段 去重后计；底价段免请求）。 */
        private int maxPerFetch = 300;
        /** 低于该价的件不自动精估；默认 0 表示覆盖所有件。 */
        private double minPrice = 0.0d;
        /** 精估请求间隔（比 catalog 同步更保守，降低触发限流的概率）。 */
        private long requestIntervalMillis = 6000L;
        /** 被 BUFF 限流后的冷却等待，冷却结束自动续跑。 */
        private long rateLimitCooldownMillis = 120000L;
        /** 单次后台精估最多冷却续跑次数，超过则放弃（下次拉取库存会重新触发）。 */
        private int maxCooldowns = 12;
    }

    @Data
    public static class QrLogin {
        private boolean qrcodeEnabled = true;
        private long qrcodeTimeoutSeconds = 300L;
        private long qrcodePollIntervalMillis = 2000L;
        private boolean browserHeadless = true;
    }
}
