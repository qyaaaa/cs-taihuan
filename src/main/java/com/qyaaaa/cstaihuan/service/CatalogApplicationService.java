package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
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
    private static final long GOODS_DETAIL_INTERVAL_MILLIS = 350L;

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
        InventorySnapshotRecord snapshot = resolveSnapshot(request == null ? null : request.getSnapshotId());
        List<BuffItem> inventory = inventorySnapshotStoreService.loadItems(snapshot.getId());
        if (inventory.isEmpty()) {
            throw new IllegalArgumentException("当前库存快照为空，无法生成 Catalog。");
        }

        String cookie = buffSessionService.resolveCookie(null);
        Set<String> seedGoodsIds = new LinkedHashSet<String>();
        for (BuffItem item : inventory) {
            if (item.getGoodsId() != null && !item.getGoodsId().trim().isEmpty()) {
                seedGoodsIds.add(item.getGoodsId().trim());
            }
        }
        if (seedGoodsIds.isEmpty()) {
            throw new IllegalArgumentException("当前库存快照中缺少有效的 goods_id，无法同步 Catalog。");
        }

        log.info("Catalog sync started, snapshotId={}, seedItemCount={}, seedGoodsCount={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(inventory.size()), Integer.valueOf(seedGoodsIds.size()));

        Deque<String> queue = new ArrayDeque<String>(seedGoodsIds);
        Set<String> discoveredGoodsIds = new LinkedHashSet<String>(seedGoodsIds);
        Set<String> visitedGoodsIds = new LinkedHashSet<String>();
        Map<String, CatalogSkin> catalogByIdentity = new LinkedHashMap<String, CatalogSkin>();

        while (!queue.isEmpty()) {
            String goodsId = queue.removeFirst();
            if (!visitedGoodsIds.add(goodsId)) {
                continue;
            }

            Map<String, Object> payload = buffApiClient.fetchGoodsDetail(
                buffProperties.getBaseUrl(),
                cookie,
                buffProperties.getGame(),
                goodsId
            );

            CatalogSkin skin = buffApiClient.parseCatalogSkinFromGoodsDetail(payload);
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
            for (String relatedGoodsId : relatedGoodsIds) {
                if (relatedGoodsId == null || relatedGoodsId.trim().isEmpty()) {
                    continue;
                }
                String normalizedGoodsId = relatedGoodsId.trim();
                if (discoveredGoodsIds.add(normalizedGoodsId)) {
                    queue.addLast(normalizedGoodsId);
                }
            }
            sleepBetweenGoodsDetailRequests();
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

        int persistedCount = catalogService.replaceAll(items);
        String message = persistedCount > 0
            ? "已根据数据库库存和 BUFF 市场目录同步 Catalog。"
            : "未能从 BUFF 目录中解析出可用的 Catalog 数据，请检查会话或库存快照。";
        log.info("Catalog sync finished, snapshotId={}, discoveredGoodsCount={}, persistedItemCount={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(discoveredGoodsIds.size()), Integer.valueOf(persistedCount));

        return new SyncCatalogResponse(
            Long.valueOf(snapshot.getId()),
            inventory.size(),
            seedGoodsIds.size(),
            discoveredGoodsIds.size(),
            persistedCount,
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

    private void sleepBetweenGoodsDetailRequests() {
        try {
            Thread.sleep(GOODS_DETAIL_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Catalog sync was interrupted.", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
