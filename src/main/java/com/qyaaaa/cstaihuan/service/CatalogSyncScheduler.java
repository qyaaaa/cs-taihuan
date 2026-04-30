package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
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
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CatalogSyncScheduler(CatalogApplicationService catalogApplicationService, BuffProperties buffProperties) {
        this.catalogApplicationService = catalogApplicationService;
        this.buffProperties = buffProperties;
    }

    @Scheduled(
        initialDelayString = "${buff.catalog-sync.scheduled-initial-delay-millis:60000}",
        fixedDelayString = "${buff.catalog-sync.scheduled-fixed-delay-millis:900000}"
    )
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
            SyncCatalogResponse response = catalogApplicationService.syncCatalog(request);
            log.info("Scheduled catalog sync finished, snapshotId={}, processedGoodsCount={}, remainingGoodsCount={}, partial={}, message={}",
                response.getSnapshotId(),
                Integer.valueOf(response.getProcessedGoodsCount()),
                Integer.valueOf(response.getRemainingGoodsCount()),
                Boolean.valueOf(response.isPartial()),
                response.getMessage());
        } catch (IllegalArgumentException ex) {
            log.info("Scheduled catalog sync skipped: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("Scheduled catalog sync failed: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }
}
