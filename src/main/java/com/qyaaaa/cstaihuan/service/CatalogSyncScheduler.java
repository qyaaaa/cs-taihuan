package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncScheduler.class);

    private final CatalogApplicationService catalogApplicationService;
    private final BuffProperties buffProperties;
    private final BuffAccountService buffAccountService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CatalogSyncScheduler(CatalogApplicationService catalogApplicationService, BuffProperties buffProperties, BuffAccountService buffAccountService) {
        this.catalogApplicationService = catalogApplicationService;
        this.buffProperties = buffProperties;
        this.buffAccountService = buffAccountService;
    }

    @Scheduled(
        initialDelayString = "${buff.catalog-sync.scheduled-initial-delay-millis:60000}",
        fixedDelayString = "${buff.catalog-sync.scheduled-fixed-delay-millis:900000}"
    )
    // 后台温和补齐最新库存快照的 catalog：限制单次请求量，真正的并发互斥由 CatalogApplicationService 兜底。
    public void syncLatestSnapshotCatalog() {
        BuffProperties.CatalogSync catalogSync = buffProperties.getCatalogSync();
        if (catalogSync == null || !catalogSync.isScheduledEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("Skip scheduled catalog sync because previous run is still active.");
            return;
        }
        try {
            SyncCatalogRequest request = new SyncCatalogRequest();
            int maxDetailRequests = catalogSync.getScheduledMaxDetailRequestsPerRun();
            if (maxDetailRequests > 0) {
                request.setMaxDetailRequests(Integer.valueOf(maxDetailRequests));
            }
            for (BuffAccount account : buffAccountService.listAccounts()) {
                try {
                    SyncCatalogResponse response = catalogApplicationService.syncCatalog(account.getId(), request);
                    log.info("Scheduled catalog sync finished, accountId={}, snapshotId={}, processedGoodsCount={}, remainingGoodsCount={}, partial={}, message={}",
                        Long.valueOf(account.getId()),
                        response.getSnapshotId(),
                        Integer.valueOf(response.getProcessedGoodsCount()),
                        Integer.valueOf(response.getRemainingGoodsCount()),
                        Boolean.valueOf(response.isPartial()),
                        response.getMessage());
                } catch (IllegalArgumentException ex) {
                    log.info("Scheduled catalog sync skipped for accountId={}: {}", Long.valueOf(account.getId()), ex.getMessage());
                }
            }
        } catch (IllegalArgumentException ex) {
            log.info("Scheduled catalog sync skipped: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("Scheduled catalog sync failed: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }
}
