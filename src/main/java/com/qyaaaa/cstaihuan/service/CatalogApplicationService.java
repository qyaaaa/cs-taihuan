package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CatalogApplicationService {
    private static final Logger log = LoggerFactory.getLogger(CatalogApplicationService.class);
    private static final int UNLIMITED_DETAIL_REQUESTS = Integer.MAX_VALUE;

    private final CatalogService catalogService;
    private final InventorySnapshotStoreService inventorySnapshotStoreService;
    private final BuffSessionService buffSessionService;
    private final BuffApiClient buffApiClient;
    private final BuffProperties buffProperties;
    private final CatalogSyncTaskStoreService catalogSyncTaskStoreService;
    private final BuffAccountService buffAccountService;
    private final AtomicBoolean catalogSyncRunning = new AtomicBoolean(false);

    public CatalogApplicationService(CatalogService catalogService, InventorySnapshotStoreService inventorySnapshotStoreService, BuffSessionService buffSessionService, BuffApiClient buffApiClient, BuffProperties buffProperties, CatalogSyncTaskStoreService catalogSyncTaskStoreService, BuffAccountService buffAccountService) {
        this.catalogService = catalogService;
        this.inventorySnapshotStoreService = inventorySnapshotStoreService;
        this.buffSessionService = buffSessionService;
        this.buffApiClient = buffApiClient;
        this.buffProperties = buffProperties;
        this.catalogSyncTaskStoreService = catalogSyncTaskStoreService;
        this.buffAccountService = buffAccountService;
    }

    public SyncCatalogResponse syncCatalog(SyncCatalogRequest request) throws Exception {
        return syncCatalog(buffAccountService.resolveDefaultAccountId(), request);
    }

    public SyncCatalogResponse syncCatalog(long accountId, SyncCatalogRequest request) throws Exception {
        return syncCatalogWithLock(accountId, request, null);
    }

    public SyncCatalogResponse syncCatalogAsync(SyncCatalogRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return syncCatalogAsync(buffAccountService.resolveDefaultAccountId(), request, progress);
    }

    public SyncCatalogResponse syncCatalogAsync(long accountId, SyncCatalogRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return syncCatalogWithLock(accountId, request, progress);
    }

    // 手动同步、异步任务和定时任务共用同一把服务级锁，避免同时打 BUFF 详情接口导致限流。
    private SyncCatalogResponse syncCatalogWithLock(long accountId, SyncCatalogRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        if (!catalogSyncRunning.compareAndSet(false, true)) {
            throw new IllegalArgumentException(ErrorMessages.CATALOG_SYNC_ALREADY_RUNNING);
        }
        try {
            return syncCatalogInternal(accountId, request, progress);
        } finally {
            catalogSyncRunning.set(false);
        }
    }

    // 从库存 goods_id 建立 catalog 同步队列，按缓存新鲜度跳过已获取项，并持续发现关联 goods 扩充产物池。
    private SyncCatalogResponse syncCatalogInternal(long accountId, SyncCatalogRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        buffAccountService.requireAccount(accountId);
        InventorySnapshotRecord snapshot = resolveSnapshot(accountId, request == null ? null : request.getSnapshotId());
        List<BuffItem> inventory = inventorySnapshotStoreService.loadItems(snapshot.getId());
        if (inventory.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.CATALOG_SYNC_EMPTY_INVENTORY);
        }
        if (progress != null) {
            progress.update(2, null, null, "已载入库存快照 #" + snapshot.getId() + "，正在准备 Catalog 同步队列。");
        }

        String cookie = buffSessionService.resolveCookie(accountId, null);
        Set<String> seedGoodsIds = new LinkedHashSet<String>();
        Map<String, String> seedCollectionsByGoodsId = new LinkedHashMap<String, String>();
        for (BuffItem item : inventory) {
            if (item.getGoodsId() != null && !item.getGoodsId().trim().isEmpty()) {
                String goodsId = item.getGoodsId().trim();
                seedGoodsIds.add(goodsId);
                if (!seedCollectionsByGoodsId.containsKey(goodsId)) {
                    seedCollectionsByGoodsId.put(goodsId, item.getCollection() == null ? null : item.getCollection().trim());
                }
            }
        }
        if (seedGoodsIds.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.CATALOG_SYNC_MISSING_GOODS_ID);
        }

        int maxDetailRequests = resolveMaxDetailRequests(request);
        long requestIntervalMillis = resolveRequestIntervalMillis();
        long cacheFreshMillis = resolveCacheFreshMillis();
        long freshAfterTimestamp = System.currentTimeMillis() - cacheFreshMillis;
        Set<String> freshGoodsIds = catalogService.loadFreshGoodsIds(freshAfterTimestamp);
        Map<String, String> seedGoodsToSyncByGoodsId = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : seedCollectionsByGoodsId.entrySet()) {
            if (!freshGoodsIds.contains(entry.getKey())) {
                seedGoodsToSyncByGoodsId.put(entry.getKey(), entry.getValue());
            }
        }

        log.info("Catalog sync started, accountId={}, snapshotId={}, seedItemCount={}, seedGoodsCount={}, freshCatalogGoodsCount={}, maxDetailRequests={}, requestIntervalMillis={}, cacheFreshMillis={}",
            Long.valueOf(accountId), Long.valueOf(snapshot.getId()), Integer.valueOf(inventory.size()), Integer.valueOf(seedGoodsIds.size()), Integer.valueOf(freshGoodsIds.size()),
            detailRequestBudgetLabel(maxDetailRequests), Long.valueOf(requestIntervalMillis), Long.valueOf(cacheFreshMillis));

        catalogSyncTaskStoreService.resetProcessing(snapshot.getId());
        catalogSyncTaskStoreService.enqueue(accountId, snapshot.getId(), seedGoodsToSyncByGoodsId);
        Map<String, CatalogSkin> catalogByIdentity = new LinkedHashMap<String, CatalogSkin>();
        int processedGoodsCount = 0;
        int skippedExistingCount = seedGoodsIds.size() - seedGoodsToSyncByGoodsId.size();
        boolean partial = false;
        if (progress != null) {
            int remainingGoodsCount = catalogSyncTaskStoreService.countOpen(snapshot.getId());
            progress.update(5, Integer.valueOf(0), Integer.valueOf(progressTotal(processedGoodsCount, maxDetailRequests, remainingGoodsCount)), "Catalog 队列已创建，待处理 goods 数：" + remainingGoodsCount + "，1 小时内已获取的 goods 会直接跳过。");
        }

        while (processedGoodsCount < maxDetailRequests) {
            Optional<CatalogSyncTaskRecord> nextTask = catalogSyncTaskStoreService.claimNext(snapshot.getId());
            if (!nextTask.isPresent()) {
                break;
            }
            CatalogSyncTaskRecord task = nextTask.get();
            String goodsId = task.getGoodsId();

            if (freshGoodsIds.contains(goodsId)) {
                skippedExistingCount++;
                catalogSyncTaskStoreService.markSkipped(task.getId(), "目录数据 1 小时内已获取，跳过重复请求。");
                continue;
            }

            Map<String, Object> payload;
            try {
                if (progress != null) {
                    int remainingGoodsCount = catalogSyncTaskStoreService.countOpen(snapshot.getId());
                    progress.update(catalogProgress(processedGoodsCount, maxDetailRequests, remainingGoodsCount), Integer.valueOf(processedGoodsCount), Integer.valueOf(progressTotal(processedGoodsCount, maxDetailRequests, remainingGoodsCount)), "正在同步 goods_id=" + goodsId + "。");
                }
                payload = buffApiClient.fetchGoodsDetail(
                    buffProperties.getBaseUrl(),
                    cookie,
                    buffProperties.getGame(),
                    goodsId
                );
            } catch (BuffRateLimitException ex) {
                partial = true;
                catalogSyncTaskStoreService.requeue(task.getId(), ex.getMessage());
                if (!catalogByIdentity.isEmpty()) {
                    int persistedCount = catalogService.upsertAll(new ArrayList<CatalogSkin>(catalogByIdentity.values()));
                    String message = buildMessage(persistedCount, processedGoodsCount, catalogSyncTaskStoreService.countOpen(snapshot.getId()), true, true);
                    log.warn("Catalog sync rate limited after partial progress, snapshotId={}, processedGoodsCount={}, remainingGoodsCount={}, persistedItemCount={}",
                        Long.valueOf(snapshot.getId()), Integer.valueOf(processedGoodsCount), Integer.valueOf(catalogSyncTaskStoreService.countOpen(snapshot.getId())), Integer.valueOf(persistedCount));
                    if (progress != null) {
                        int remainingGoodsCount = catalogSyncTaskStoreService.countOpen(snapshot.getId());
                        progress.update(100, Integer.valueOf(processedGoodsCount), Integer.valueOf(progressTotal(processedGoodsCount, maxDetailRequests, remainingGoodsCount)), message);
                    }
                    return new SyncCatalogResponse(
                        Long.valueOf(snapshot.getId()),
                        inventory.size(),
                        seedGoodsIds.size(),
                        catalogSyncTaskStoreService.countAll(snapshot.getId()),
                        processedGoodsCount,
                        skippedExistingCount,
                        catalogSyncTaskStoreService.countOpen(snapshot.getId()),
                        persistedCount,
                        true,
                        message
                    );
                }
                throw ex;
            } catch (Exception ex) {
                catalogSyncTaskStoreService.markFailed(task.getId(), ex.getMessage());
                log.warn("Catalog goods sync failed, snapshotId={}, goodsId={}, retryCount={}, reason={}",
                    Long.valueOf(snapshot.getId()), goodsId, Integer.valueOf(task.getRetryCount()), ex.getMessage());
                continue;
            }
            processedGoodsCount++;
            if (progress != null) {
                int remainingGoodsCount = catalogSyncTaskStoreService.countOpen(snapshot.getId());
                progress.update(catalogProgress(processedGoodsCount, maxDetailRequests, remainingGoodsCount), Integer.valueOf(processedGoodsCount), Integer.valueOf(progressTotal(processedGoodsCount, maxDetailRequests, remainingGoodsCount)), "已处理 " + processedGoodsCount + " 个 goods，当前队列剩余 " + remainingGoodsCount + " 个。");
            }

            try {
                CatalogSkin skin = buffApiClient.parseCatalogSkinFromGoodsDetail(payload, task.getCollection());
                String derivedCollection = skin == null ? task.getCollection() : skin.getCollection();
                List<CatalogSkin> parsedSkins = new ArrayList<CatalogSkin>();
                if (skin != null) {
                    parsedSkins.add(skin);
                }
                parsedSkins.addAll(buffApiClient.extractRelatedCatalogSkins(payload, derivedCollection));
                Map<String, CatalogSkin> parsedByIdentity = new LinkedHashMap<String, CatalogSkin>();
                for (CatalogSkin parsedSkin : parsedSkins) {
                    String identity = safe(parsedSkin.getGoodsId());
                    if (identity.isEmpty()) {
                        identity = safe(parsedSkin.getName());
                    }
                    if (!identity.isEmpty()) {
                        parsedByIdentity.put(identity, parsedSkin);
                        if (parsedSkin.getGoodsId() != null && !parsedSkin.getGoodsId().trim().isEmpty()) {
                            freshGoodsIds.add(parsedSkin.getGoodsId().trim());
                        }
                    }
                }
                if (!parsedByIdentity.isEmpty()) {
                    catalogByIdentity.putAll(parsedByIdentity);
                    catalogService.upsertAll(new ArrayList<CatalogSkin>(parsedByIdentity.values()));
                }

                List<String> relatedGoodsIds = buffApiClient.extractRelatedGoodsIds(payload);
                for (String relatedGoodsId : relatedGoodsIds) {
                    if (relatedGoodsId == null || relatedGoodsId.trim().isEmpty()) {
                        continue;
                    }
                    String normalizedGoodsId = relatedGoodsId.trim();
                    if (freshGoodsIds.contains(normalizedGoodsId)) {
                        continue;
                    }
                    catalogSyncTaskStoreService.enqueue(accountId, snapshot.getId(), normalizedGoodsId, derivedCollection);
                }
                catalogSyncTaskStoreService.markSucceeded(task.getId());
            } catch (Exception ex) {
                catalogSyncTaskStoreService.markFailed(task.getId(), ex.getMessage());
                log.warn("Catalog goods parse or persist failed, snapshotId={}, goodsId={}, retryCount={}, reason={}",
                    Long.valueOf(snapshot.getId()), goodsId, Integer.valueOf(task.getRetryCount()), ex.getMessage());
            }
            if (catalogSyncTaskStoreService.countOpen(snapshot.getId()) > 0 && processedGoodsCount < maxDetailRequests) {
                sleepBetweenGoodsDetailRequests(requestIntervalMillis, progress);
            }
        }
        if (!isUnlimited(maxDetailRequests) && processedGoodsCount >= maxDetailRequests && catalogSyncTaskStoreService.countOpen(snapshot.getId()) > 0) {
            partial = true;
            log.info("Catalog sync request budget reached, snapshotId={}, processedGoodsCount={}, openGoodsCount={}",
                Long.valueOf(snapshot.getId()), Integer.valueOf(processedGoodsCount), Integer.valueOf(catalogSyncTaskStoreService.countOpen(snapshot.getId())));
        }

        if (progress != null) {
            int remainingGoodsCount = catalogSyncTaskStoreService.countOpen(snapshot.getId());
            progress.update(94, Integer.valueOf(processedGoodsCount), Integer.valueOf(progressTotal(processedGoodsCount, maxDetailRequests, remainingGoodsCount)), "Catalog 请求完成，正在写入数据库。");
        }
        List<CatalogSkin> items = new ArrayList<CatalogSkin>(catalogByIdentity.values());
        Collections.sort(items, new Comparator<CatalogSkin>() {
            public int compare(CatalogSkin left, CatalogSkin right) {
                int byCollection = safe(left.getCollection()).compareTo(safe(right.getCollection()));
                if (byCollection != 0) {
                    return byCollection;
                }
                int byRarity = safe(left.getRarity()).compareTo(safe(right.getRarity()));
                if (byRarity != 0) {
                    return byRarity;
                }
                return safe(left.getName()).compareTo(safe(right.getName()));
            }
        });

        int persistedCount = catalogService.upsertAll(items);
        int remainingGoodsCount = catalogSyncTaskStoreService.countOpen(snapshot.getId());
        partial = partial || remainingGoodsCount > 0;
        String message = processedGoodsCount <= 0 && skippedExistingCount > 0 && remainingGoodsCount <= 0
            ? "目录数据 1 小时内已获取，已跳过 BUFF 请求。"
            : buildMessage(persistedCount, processedGoodsCount, remainingGoodsCount, partial, false);
        log.info("Catalog sync finished, snapshotId={}, discoveredGoodsCount={}, processedGoodsCount={}, skippedExistingCount={}, remainingGoodsCount={}, persistedItemCount={}, partial={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(catalogSyncTaskStoreService.countAll(snapshot.getId())), Integer.valueOf(processedGoodsCount),
            Integer.valueOf(skippedExistingCount), Integer.valueOf(remainingGoodsCount), Integer.valueOf(persistedCount), Boolean.valueOf(partial));
        if (progress != null) {
            progress.update(100, Integer.valueOf(processedGoodsCount), Integer.valueOf(progressTotal(processedGoodsCount, maxDetailRequests, remainingGoodsCount)), message);
        }

        return new SyncCatalogResponse(
            Long.valueOf(snapshot.getId()),
            inventory.size(),
            seedGoodsIds.size(),
            catalogSyncTaskStoreService.countAll(snapshot.getId()),
            processedGoodsCount,
            skippedExistingCount,
            remainingGoodsCount,
            persistedCount,
            partial,
            message
        );
    }

    private InventorySnapshotRecord resolveSnapshot(long accountId, Long snapshotId) {
        if (snapshotId != null) {
            Optional<InventorySnapshotRecord> snapshot = inventorySnapshotStoreService.findById(accountId, snapshotId.longValue());
            if (!snapshot.isPresent()) {
                throw new IllegalArgumentException(ErrorMessages.inventorySnapshotNotFound(snapshotId));
            }
            return snapshot.get();
        }

        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(accountId, buffProperties.getGame());
        if (!latest.isPresent()) {
            throw new IllegalArgumentException(ErrorMessages.NO_PERSISTED_INVENTORY_SNAPSHOT);
        }
        return latest.get();
    }

    private int resolveMaxDetailRequests(SyncCatalogRequest request) {
        if (request != null && request.getMaxDetailRequests() != null) {
            return Math.max(1, request.getMaxDetailRequests().intValue());
        }
        int configured = buffProperties.getCatalogSync().getMaxDetailRequestsPerRun();
        return configured <= 0 ? UNLIMITED_DETAIL_REQUESTS : configured;
    }

    private long resolveRequestIntervalMillis() {
        return Math.max(1000L, buffProperties.getCatalogSync().getRequestIntervalMillis());
    }

    private long resolveCacheFreshMillis() {
        return Math.max(0L, buffProperties.getCatalogSync().getCacheFreshMillis());
    }

    private int catalogProgress(int processedGoodsCount, int maxDetailRequests, int remainingGoodsCount) {
        if (isUnlimited(maxDetailRequests)) {
            int total = processedGoodsCount + Math.max(0, remainingGoodsCount);
            if (total <= 0) {
                return 5;
            }
            return Math.min(92, 5 + (int) Math.floor((double) processedGoodsCount * 87.0d / (double) total));
        }
        if (maxDetailRequests <= 0) {
            return 5;
        }
        return Math.min(92, 5 + (int) Math.floor((double) processedGoodsCount * 87.0d / (double) maxDetailRequests));
    }

    private int progressTotal(int processedGoodsCount, int maxDetailRequests, int remainingGoodsCount) {
        if (isUnlimited(maxDetailRequests)) {
            return Math.max(1, Math.max(processedGoodsCount, processedGoodsCount + Math.max(0, remainingGoodsCount)));
        }
        return maxDetailRequests;
    }

    private boolean isUnlimited(int maxDetailRequests) {
        return maxDetailRequests == UNLIMITED_DETAIL_REQUESTS;
    }

    private String detailRequestBudgetLabel(int maxDetailRequests) {
        return isUnlimited(maxDetailRequests) ? "ALL" : String.valueOf(maxDetailRequests);
    }

    private void sleepBetweenGoodsDetailRequests(long intervalMillis, AsyncTaskService.TaskProgress progress) {
        try {
            if (progress != null) {
                progress.message("等待 " + (intervalMillis / 1000L) + " 秒后继续同步下一个 goods，降低 BUFF 限流概率。");
            }
            Thread.sleep(intervalMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ErrorMessages.CATALOG_SYNC_INTERRUPTED, ex);
        }
    }

    private String buildMessage(int persistedCount, int processedGoodsCount, int remainingGoodsCount, boolean partial, boolean limited) {
        if (persistedCount <= 0 && processedGoodsCount <= 0) {
            return "未能从 BUFF 目录中解析出可用的目录数据，请检查会话或库存快照。";
        }
        if (limited) {
            return "BUFF 当前触发限流，本次已先保存部分目录数据。请稍后继续同步，剩余待处理 goods 数：" + Math.max(0, remainingGoodsCount) + "。";
        }
        if (partial) {
            return "目录数据已分批同步，本次处理 " + processedGoodsCount + " 个 goods，剩余待处理 " + Math.max(0, remainingGoodsCount) + " 个。可稍后继续同步补全。";
        }
        return "已根据数据库库存和 BUFF 市场目录同步目录数据。";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
