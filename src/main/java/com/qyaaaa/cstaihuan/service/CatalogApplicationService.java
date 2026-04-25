package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CatalogApplicationService {
    private static final Logger log = LoggerFactory.getLogger(CatalogApplicationService.class);

    private final CatalogService catalogService;
    private final InventorySnapshotStoreService inventorySnapshotStoreService;
    private final BuffSessionService buffSessionService;
    private final BuffApiClient buffApiClient;
    private final BuffProperties buffProperties;

    public CatalogApplicationService(CatalogService catalogService, InventorySnapshotStoreService inventorySnapshotStoreService, BuffSessionService buffSessionService, BuffApiClient buffApiClient, BuffProperties buffProperties) {
        this.catalogService = catalogService;
        this.inventorySnapshotStoreService = inventorySnapshotStoreService;
        this.buffSessionService = buffSessionService;
        this.buffApiClient = buffApiClient;
        this.buffProperties = buffProperties;
    }

    public SyncCatalogResponse syncCatalog(SyncCatalogRequest request) throws Exception {
        return syncCatalog(request, null);
    }

    public SyncCatalogResponse syncCatalogAsync(SyncCatalogRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return syncCatalog(request, progress);
    }

    private SyncCatalogResponse syncCatalog(SyncCatalogRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        InventorySnapshotRecord snapshot = resolveSnapshot(request == null ? null : request.getSnapshotId());
        List<BuffItem> inventory = inventorySnapshotStoreService.loadItems(snapshot.getId());
        if (inventory.isEmpty()) {
            throw new IllegalArgumentException("当前库存快照为空，无法生成 Catalog。");
        }
        if (progress != null) {
            progress.update(2, null, null, "已载入库存快照 #" + snapshot.getId() + "，正在准备 Catalog 同步队列。");
        }

        String cookie = buffSessionService.resolveCookie(null);
        Set<String> seedGoodsIds = new LinkedHashSet<String>();
        Map<String, String> seedCollectionsByGoodsId = new LinkedHashMap<String, String>();
        for (BuffItem item : inventory) {
            if (item.getGoodsId() != null && !item.getGoodsId().trim().isEmpty()) {
                String goodsId = item.getGoodsId().trim();
                seedGoodsIds.add(goodsId);
                if (!seedCollectionsByGoodsId.containsKey(goodsId) && item.getCollection() != null && !item.getCollection().trim().isEmpty()) {
                    seedCollectionsByGoodsId.put(goodsId, item.getCollection().trim());
                }
            }
        }
        if (seedGoodsIds.isEmpty()) {
            throw new IllegalArgumentException("当前库存快照中缺少有效的 goods_id，无法同步 Catalog。");
        }

        int maxDetailRequests = resolveMaxDetailRequests(request);
        long requestIntervalMillis = resolveRequestIntervalMillis();
        Set<String> existingGoodsIds = catalogService.loadExistingGoodsIds();

        log.info("Catalog sync started, snapshotId={}, seedItemCount={}, seedGoodsCount={}, existingCatalogGoodsCount={}, maxDetailRequests={}, requestIntervalMillis={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(inventory.size()), Integer.valueOf(seedGoodsIds.size()), Integer.valueOf(existingGoodsIds.size()),
            Integer.valueOf(maxDetailRequests), Long.valueOf(requestIntervalMillis));

        Deque<CatalogSyncTask> queue = buildInitialQueue(seedGoodsIds, seedCollectionsByGoodsId, existingGoodsIds);
        Set<String> discoveredGoodsIds = new LinkedHashSet<String>(seedGoodsIds);
        Set<String> visitedGoodsIds = new LinkedHashSet<String>();
        Map<String, CatalogSkin> catalogByIdentity = new LinkedHashMap<String, CatalogSkin>();
        int processedGoodsCount = 0;
        int skippedExistingCount = 0;
        boolean partial = false;
        if (progress != null) {
            progress.update(5, Integer.valueOf(0), Integer.valueOf(maxDetailRequests), "Catalog 队列已创建，待处理 goods 数：" + queue.size() + "。");
        }

        while (!queue.isEmpty()) {
            if (processedGoodsCount >= maxDetailRequests) {
                partial = true;
                log.info("Catalog sync request budget reached, snapshotId={}, processedGoodsCount={}, queuedGoodsCount={}",
                    Long.valueOf(snapshot.getId()), Integer.valueOf(processedGoodsCount), Integer.valueOf(queue.size()));
                break;
            }

            CatalogSyncTask task = queue.removeFirst();
            String goodsId = task.goodsId;
            if (!visitedGoodsIds.add(goodsId)) {
                continue;
            }

            boolean seededGoods = seedGoodsIds.contains(goodsId);
            if (existingGoodsIds.contains(goodsId) && !seededGoods) {
                skippedExistingCount++;
                continue;
            }

            Map<String, Object> payload;
            try {
                if (progress != null) {
                    progress.update(catalogProgress(processedGoodsCount, maxDetailRequests), Integer.valueOf(processedGoodsCount), Integer.valueOf(maxDetailRequests), "正在同步 goods_id=" + goodsId + "。");
                }
                payload = buffApiClient.fetchGoodsDetail(
                    buffProperties.getBaseUrl(),
                    cookie,
                    buffProperties.getGame(),
                    goodsId
                );
            } catch (BuffRateLimitException ex) {
                partial = true;
                if (!catalogByIdentity.isEmpty()) {
                    int persistedCount = catalogService.upsertAll(new ArrayList<CatalogSkin>(catalogByIdentity.values()));
                    String message = buildMessage(persistedCount, processedGoodsCount, queue.size() + 1, true, true);
                    log.warn("Catalog sync rate limited after partial progress, snapshotId={}, processedGoodsCount={}, remainingGoodsCount={}, persistedItemCount={}",
                        Long.valueOf(snapshot.getId()), Integer.valueOf(processedGoodsCount), Integer.valueOf(queue.size() + 1), Integer.valueOf(persistedCount));
                    if (progress != null) {
                        progress.update(100, Integer.valueOf(processedGoodsCount), Integer.valueOf(maxDetailRequests), message);
                    }
                    return new SyncCatalogResponse(
                        Long.valueOf(snapshot.getId()),
                        inventory.size(),
                        seedGoodsIds.size(),
                        discoveredGoodsIds.size(),
                        processedGoodsCount,
                        skippedExistingCount,
                        queue.size() + 1,
                        persistedCount,
                        true,
                        message
                    );
                }
                throw ex;
            }
            processedGoodsCount++;
            if (progress != null) {
                progress.update(catalogProgress(processedGoodsCount, maxDetailRequests), Integer.valueOf(processedGoodsCount), Integer.valueOf(maxDetailRequests), "已处理 " + processedGoodsCount + " 个 goods，当前队列剩余 " + queue.size() + " 个。");
            }

            CatalogSkin skin = buffApiClient.parseCatalogSkinFromGoodsDetail(payload, task.collection);
            if (skin != null) {
                String identity = safe(skin.getGoodsId());
                if (identity.isEmpty()) {
                    identity = safe(skin.getName());
                }
                if (!identity.isEmpty()) {
                    catalogByIdentity.put(identity, skin);
                }
            }

            List<String> relatedGoodsIds = buffApiClient.extractRelatedGoodsIds(payload);
            String derivedCollection = skin == null ? task.collection : skin.getCollection();
            for (String relatedGoodsId : relatedGoodsIds) {
                if (relatedGoodsId == null || relatedGoodsId.trim().isEmpty()) {
                    continue;
                }
                String normalizedGoodsId = relatedGoodsId.trim();
                if (discoveredGoodsIds.add(normalizedGoodsId)) {
                    queue.addFirst(new CatalogSyncTask(normalizedGoodsId, derivedCollection));
                }
            }
            if (!queue.isEmpty() && processedGoodsCount < maxDetailRequests) {
                sleepBetweenGoodsDetailRequests(requestIntervalMillis, progress);
            }
        }

        if (progress != null) {
            progress.update(94, Integer.valueOf(processedGoodsCount), Integer.valueOf(maxDetailRequests), "Catalog 请求完成，正在写入数据库。");
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
        int remainingGoodsCount = queue.size();
        String message = buildMessage(persistedCount, processedGoodsCount, remainingGoodsCount, partial, false);
        log.info("Catalog sync finished, snapshotId={}, discoveredGoodsCount={}, processedGoodsCount={}, skippedExistingCount={}, remainingGoodsCount={}, persistedItemCount={}, partial={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(discoveredGoodsIds.size()), Integer.valueOf(processedGoodsCount),
            Integer.valueOf(skippedExistingCount), Integer.valueOf(remainingGoodsCount), Integer.valueOf(persistedCount), Boolean.valueOf(partial));
        if (progress != null) {
            progress.update(100, Integer.valueOf(processedGoodsCount), Integer.valueOf(maxDetailRequests), message);
        }

        return new SyncCatalogResponse(
            Long.valueOf(snapshot.getId()),
            inventory.size(),
            seedGoodsIds.size(),
            discoveredGoodsIds.size(),
            processedGoodsCount,
            skippedExistingCount,
            remainingGoodsCount,
            persistedCount,
            partial,
            message
        );
    }

    private InventorySnapshotRecord resolveSnapshot(Long snapshotId) {
        if (snapshotId != null) {
            Optional<InventorySnapshotRecord> snapshot = inventorySnapshotStoreService.findById(snapshotId.longValue());
            if (!snapshot.isPresent()) {
                throw new IllegalArgumentException("Inventory snapshot was not found: " + snapshotId);
            }
            return snapshot.get();
        }

        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(buffProperties.getGame());
        if (!latest.isPresent()) {
            throw new IllegalArgumentException("No persisted inventory snapshot was found.");
        }
        return latest.get();
    }

    private Deque<CatalogSyncTask> buildInitialQueue(Set<String> seedGoodsIds, Map<String, String> seedCollectionsByGoodsId, Set<String> existingGoodsIds) {
        Deque<CatalogSyncTask> queue = new ArrayDeque<CatalogSyncTask>();
        for (String goodsId : seedGoodsIds) {
            if (!existingGoodsIds.contains(goodsId)) {
                queue.addLast(new CatalogSyncTask(goodsId, seedCollectionsByGoodsId.get(goodsId)));
            }
        }
        for (String goodsId : seedGoodsIds) {
            if (existingGoodsIds.contains(goodsId)) {
                queue.addLast(new CatalogSyncTask(goodsId, seedCollectionsByGoodsId.get(goodsId)));
            }
        }
        return queue;
    }

    private static final class CatalogSyncTask {
        private final String goodsId;
        private final String collection;

        private CatalogSyncTask(String goodsId, String collection) {
            this.goodsId = goodsId;
            this.collection = collection;
        }
    }

    private int resolveMaxDetailRequests(SyncCatalogRequest request) {
        int configured = buffProperties.getCatalogSync().getMaxDetailRequestsPerRun();
        if (request == null || request.getMaxDetailRequests() == null) {
            return configured;
        }
        return Math.max(1, request.getMaxDetailRequests().intValue());
    }

    private long resolveRequestIntervalMillis() {
        return Math.max(1000L, buffProperties.getCatalogSync().getRequestIntervalMillis());
    }

    private int catalogProgress(int processedGoodsCount, int maxDetailRequests) {
        if (maxDetailRequests <= 0) {
            return 5;
        }
        return Math.min(92, 5 + (int) Math.floor((double) processedGoodsCount * 87.0d / (double) maxDetailRequests));
    }

    private void sleepBetweenGoodsDetailRequests(long intervalMillis, AsyncTaskService.TaskProgress progress) {
        try {
            if (progress != null) {
                progress.message("等待 " + (intervalMillis / 1000L) + " 秒后继续同步下一个 goods，降低 BUFF 限流概率。");
            }
            Thread.sleep(intervalMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Catalog sync was interrupted.", ex);
        }
    }

    private String buildMessage(int persistedCount, int processedGoodsCount, int remainingGoodsCount, boolean partial, boolean limited) {
        if (persistedCount <= 0 && processedGoodsCount <= 0) {
            return "未能从 BUFF 目录中解析出可用的 Catalog 数据，请检查会话或库存快照。";
        }
        if (limited) {
            return "BUFF 当前触发限流，本次已先保存部分 Catalog 数据。请稍后继续同步，剩余待处理 goods 数：" + Math.max(0, remainingGoodsCount) + "。";
        }
        if (partial) {
            return "Catalog 已分批同步，本次处理 " + processedGoodsCount + " 个 goods，剩余待处理 " + Math.max(0, remainingGoodsCount) + " 个。可稍后继续同步补全。";
        }
        return "已根据数据库库存和 BUFF 市场目录同步 Catalog。";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
