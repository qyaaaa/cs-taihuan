package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.TradeUpOptimizer;
import com.qyaaaa.cstaihuan.Rarity;
import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.config.TradeUpProperties;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogGroup;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogRequest;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.PersistNextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TradeUpApplicationService {
    private final InventorySnapshotStoreService inventorySnapshotStoreService;
    private final CatalogService catalogService;
    private final TradeUpProperties tradeUpProperties;
    private final BuffProperties buffProperties;
    private final TradeUpNextTierStoreService tradeUpNextTierStoreService;
    private final CatalogSyncTaskStoreService catalogSyncTaskStoreService;

    public TradeUpApplicationService(InventorySnapshotStoreService inventorySnapshotStoreService, CatalogService catalogService, TradeUpProperties tradeUpProperties, BuffProperties buffProperties, TradeUpNextTierStoreService tradeUpNextTierStoreService, CatalogSyncTaskStoreService catalogSyncTaskStoreService) {
        this.inventorySnapshotStoreService = inventorySnapshotStoreService;
        this.catalogService = catalogService;
        this.tradeUpProperties = tradeUpProperties;
        this.buffProperties = buffProperties;
        this.tradeUpNextTierStoreService = tradeUpNextTierStoreService;
        this.catalogSyncTaskStoreService = catalogSyncTaskStoreService;
    }

    public OptimizeTradeUpResponse optimize(OptimizeTradeUpRequest request) throws Exception {
        InventorySnapshotRecord snapshot = resolveSnapshot(request.getSnapshotId());
        assertCatalogSyncComplete(snapshot.getId());
        List<BuffItem> inventory = inventorySnapshotStoreService.loadItems(snapshot.getId());
        List<CatalogSkin> catalog = catalogService.loadAll();

        double saleFeeRate = request.getSaleFeeRate() == null ? tradeUpProperties.getSaleFeeRate() : request.getSaleFeeRate().doubleValue();
        int maxItemsPerRarity = request.getMaxItemsPerRarity() == null ? tradeUpProperties.getMaxItemsPerRarity() : request.getMaxItemsPerRarity().intValue();
        int maxCombinations = request.getMaxCombinations() == null ? tradeUpProperties.getMaxCombinations() : request.getMaxCombinations().intValue();
        int topK = request.getTopK() == null ? 5 : request.getTopK().intValue();

        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog, saleFeeRate, tradeUpProperties.getOutputPriceFactors(), tradeUpProperties.getOutputPriceBands());
        List<TradeUpPlan> plans = optimizer.findBestContracts(
            optimizer.enrichInventory(inventory),
            topK,
            maxItemsPerRarity,
            maxCombinations,
            request.getSortBy(),
            request.getRarity(),
            request.getTrackType(),
            request.getContractType()
        );
        return new OptimizeTradeUpResponse(plans);
    }

    public NextTierCatalogResponse loadNextTierCatalog(NextTierCatalogRequest request) throws Exception {
        InventorySnapshotRecord snapshot = resolveSnapshot(request.getSnapshotId());
        assertCatalogSyncComplete(snapshot.getId());
        List<BuffItem> inventory = inventorySnapshotStoreService.loadItems(snapshot.getId());
        List<CatalogSkin> catalog = catalogService.loadAll();

        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog, tradeUpProperties.getSaleFeeRate());
        List<BuffItem> enrichedInventory = optimizer.enrichInventory(inventory);

        Map<String, List<CatalogSkin>> targetByCollectionAndRarity = new HashMap<String, List<CatalogSkin>>();
        for (CatalogSkin skin : catalog) {
            String key = key(skin.getCollection(), skin.getRarity());
            List<CatalogSkin> rows = targetByCollectionAndRarity.get(key);
            if (rows == null) {
                rows = new ArrayList<CatalogSkin>();
                targetByCollectionAndRarity.put(key, rows);
            }
            rows.add(skin);
        }

        Map<String, NextTierCatalogGroup> grouped = new HashMap<String, NextTierCatalogGroup>();
        for (BuffItem item : enrichedInventory) {
            String baseRarity = contractRarity(item);
            if (baseRarity == null || item.getCollection() == null) {
                continue;
            }

            // 冗余关系优先取“上一档基材”，只有上一档在 catalog 中不存在时才回退到下一档产物。
            String targetRarity = resolveRedundantRarity(baseRarity, item.getCollection(), targetByCollectionAndRarity);
            if (targetRarity == null) {
                continue;
            }

            List<CatalogSkin> targetItems = targetByCollectionAndRarity.get(key(item.getCollection(), targetRarity));
            if (targetItems == null || targetItems.isEmpty()) {
                continue;
            }

            String groupKey = key(item.getCollection(), baseRarity);
            NextTierCatalogGroup group = grouped.get(groupKey);
            if (group == null) {
                group = new NextTierCatalogGroup();
                group.setCollection(item.getCollection());
                group.setBaseRarity(baseRarity);
                group.setTargetRarity(targetRarity);
                group.setInventoryCount(0);
                group.setItems(new ArrayList<CatalogSkin>(targetItems));
                grouped.put(groupKey, group);
            }
            group.setInventoryCount(group.getInventoryCount() + 1);
        }

        List<NextTierCatalogGroup> groups = new ArrayList<NextTierCatalogGroup>(grouped.values());
        Collections.sort(groups, new Comparator<NextTierCatalogGroup>() {
            public int compare(NextTierCatalogGroup left, NextTierCatalogGroup right) {
                int byCollection = safe(left.getCollection()).compareTo(safe(right.getCollection()));
                if (byCollection != 0) {
                    return byCollection;
                }
                return safe(left.getBaseRarity()).compareTo(safe(right.getBaseRarity()));
            }
        });

        int targetItemCount = 0;
        for (NextTierCatalogGroup group : groups) {
            targetItemCount += group.getItems() == null ? 0 : group.getItems().size();
        }

        return new NextTierCatalogResponse(
            Long.valueOf(snapshot.getId()),
            enrichedInventory.size(),
            groups.size(),
            targetItemCount,
            groups
        );
    }

    public PersistNextTierCatalogResponse persistNextTierCatalog(NextTierCatalogRequest request) throws Exception {
        NextTierCatalogResponse response = loadNextTierCatalog(request);
        int itemCount = tradeUpNextTierStoreService.replaceForSnapshot(response.getSnapshotId().longValue(), response.getGroups());
        String message = itemCount > 0
            ? "已批量保存关联档位冗余数据。"
            : "没有匹配到可保存的关联档位数据，请检查当前库存档位和 catalog 数据库中的收藏品/品质是否能对应上。";
        return new PersistNextTierCatalogResponse(
            response.getSnapshotId(),
            response.getGroupCount(),
            itemCount,
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

    private void assertCatalogSyncComplete(long snapshotId) {
        int discoveredCount = catalogSyncTaskStoreService.countAll(snapshotId);
        int remainingCount = catalogSyncTaskStoreService.countOpen(snapshotId);
        if (discoveredCount > 0 && remainingCount > 0) {
            throw new IllegalArgumentException("目录同步尚未完成，当前快照还有 " + remainingCount + " 个 goods 待处理。请继续点击“从 BUFF 同步目录数据”，直到剩余为 0 后再生成方案。");
        }
    }

    private String contractRarity(BuffItem item) {
        if (item == null) {
            return null;
        }
        return item.getFilterRarity() != null ? item.getFilterRarity() : item.getRarity();
    }

    private String resolveRedundantRarity(String baseRarity, String collection, Map<String, List<CatalogSkin>> targetByCollectionAndRarity) {
        String previousRarity = Rarity.previous(baseRarity);
        if (previousRarity != null && targetByCollectionAndRarity.containsKey(key(collection, previousRarity))) {
            return previousRarity;
        }

        String nextRarity = Rarity.next(baseRarity);
        if (nextRarity != null && targetByCollectionAndRarity.containsKey(key(collection, nextRarity))) {
            return nextRarity;
        }
        return null;
    }

    private String key(String collection, String rarity) {
        return safe(collection) + "||" + safe(rarity);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
