package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncScheduler.class);
    // 有效会话最多每小时复检一次，避免每轮都打 BUFF；失效账号则每轮复检，便于用户重登后快速恢复。
    private static final long SESSION_CHECK_INTERVAL_MILLIS = 3_600_000L;

    private final CatalogApplicationService catalogApplicationService;
    private final BuffProperties buffProperties;
    private final BuffAccountService buffAccountService;
    private final BuffSessionService buffSessionService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, Long> lastValidatedOkAt = new ConcurrentHashMap<Long, Long>();

    public CatalogSyncScheduler(CatalogApplicationService catalogApplicationService, BuffProperties buffProperties, BuffAccountService buffAccountService, BuffSessionService buffSessionService) {
        this.catalogApplicationService = catalogApplicationService;
        this.buffProperties = buffProperties;
        this.buffAccountService = buffAccountService;
        this.buffSessionService = buffSessionService;
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
                if (!ensureSessionAlive(account.getId())) {
                    continue;
                }
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

    // 同步前的会话健康检查：有效会话节流到每小时一次，失效则立即把账号标记为掉线并跳过本轮，
    // 让前端可以提示用户重新扫码登录，闭合“掉线→提示→重登→恢复”这条链路。
    private boolean ensureSessionAlive(long accountId) {
        long now = System.currentTimeMillis();
        Long lastOk = lastValidatedOkAt.get(Long.valueOf(accountId));
        if (lastOk != null && now - lastOk.longValue() < SESSION_CHECK_INTERVAL_MILLIS) {
            return true;
        }
        BuffSessionStatusResponse status;
        try {
            status = buffSessionService.validateSession(accountId);
        } catch (Exception ex) {
            // 没有会话记录或校验请求本身异常：跳过本轮，下轮再试，不误判为掉线。
            log.info("Scheduled catalog sync skipped, session check failed, accountId={}: {}", Long.valueOf(accountId), ex.getMessage());
            return false;
        }
        if (status.isValid()) {
            lastValidatedOkAt.put(Long.valueOf(accountId), Long.valueOf(now));
            return true;
        }
        // 失效或限流等未确认状态：本轮跳过，不缓存，下轮继续复检。
        lastValidatedOkAt.remove(Long.valueOf(accountId));
        log.info("Scheduled catalog sync skipped, BUFF session not alive, accountId={}: {}", Long.valueOf(accountId), status.getMessage());
        return false;
    }
}
