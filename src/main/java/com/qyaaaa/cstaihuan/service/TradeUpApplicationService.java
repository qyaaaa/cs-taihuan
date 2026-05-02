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
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
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
    private static final String[] CONTRACT_RARITIES = new String[] {
        "consumer",
        "industrial",
        "mil-spec",
        "restricted",
        "classified",
        "covert"
    };

    private final InventorySnapshotStoreService inventorySnapshotStoreService;
    private final CatalogService catalogService;
    private final TradeUpProperties tradeUpProperties;
    private final BuffProperties buffProperties;
    private final TradeUpNextTierStoreService tradeUpNextTierStoreService;
    private final CatalogSyncTaskStoreService catalogSyncTaskStoreService;
    private final BuffAccountService buffAccountService;

    public TradeUpApplicationService(InventorySnapshotStoreService inventorySnapshotStoreService, CatalogService catalogService, TradeUpProperties tradeUpProperties, BuffProperties buffProperties, TradeUpNextTierStoreService tradeUpNextTierStoreService, CatalogSyncTaskStoreService catalogSyncTaskStoreService, BuffAccountService buffAccountService) {
        this.inventorySnapshotStoreService = inventorySnapshotStoreService;
        this.catalogService = catalogService;
        this.tradeUpProperties = tradeUpProperties;
        this.buffProperties = buffProperties;
        this.tradeUpNextTierStoreService = tradeUpNextTierStoreService;
        this.catalogSyncTaskStoreService = catalogSyncTaskStoreService;
        this.buffAccountService = buffAccountService;
    }

    // 方案页请求入口：加载快照和 catalog 后交给优化器；全部档位时逐档取 topK，避免低档位被全局排序挤掉。
    public OptimizeTradeUpResponse optimize(OptimizeTradeUpRequest request) throws Exception {
        return optimize(buffAccountService.resolveDefaultAccountId(), request);
    }

    public OptimizeTradeUpResponse optimize(long accountId, OptimizeTradeUpRequest request) throws Exception {
        buffAccountService.requireAccount(accountId);
        InventorySnapshotRecord snapshot = resolveSnapshot(accountId, request.getSnapshotId());
        assertCatalogSyncComplete(snapshot.getId());
        List<BuffItem> inventory = inventorySnapshotStoreService.loadItems(snapshot.getId());
        List<CatalogSkin> catalog = catalogService.loadAll();

        double saleFeeRate = request.getSaleFeeRate() == null ? tradeUpProperties.getSaleFeeRate() : request.getSaleFeeRate().doubleValue();
        int maxItemsPerRarity = request.getMaxItemsPerRarity() == null ? tradeUpProperties.getMaxItemsPerRarity() : request.getMaxItemsPerRarity().intValue();
        int maxCombinations = request.getMaxCombinations() == null ? tradeUpProperties.getMaxCombinations() : request.getMaxCombinations().intValue();
        int topK = request.getTopK() == null ? 5 : request.getTopK().intValue();

        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog, saleFeeRate, tradeUpProperties.getOutputPriceFactors(), tradeUpProperties.getOutputPriceBands());
        List<BuffItem> enrichedInventory = optimizer.enrichInventory(inventory);
        List<TradeUpPlan> plans = isAllRarity(request.getRarity())
            ? findBestContractsByRarity(optimizer, enrichedInventory, topK, maxItemsPerRarity, maxCombinations, request)
            : optimizer.findBestContracts(
                enrichedInventory,
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

    // 前端选择“全部”时仍要保留每个档位的候选方案，方便进入页面后再按档位筛选。
    private List<TradeUpPlan> findBestContractsByRarity(TradeUpOptimizer optimizer, List<BuffItem> inventory, int topK, int maxItemsPerRarity, int maxCombinations, OptimizeTradeUpRequest request) {
        List<TradeUpPlan> plans = new ArrayList<TradeUpPlan>();
        for (String rarity : CONTRACT_RARITIES) {
            plans.addAll(optimizer.findBestContracts(
                inventory,
                topK,
                maxItemsPerRarity,
                maxCombinations,
                request.getSortBy(),
                rarity,
                request.getTrackType(),
                request.getContractType()
            ));
        }
        return plans;
    }

    private boolean isAllRarity(String rarity) {
        return rarity == null || rarity.trim().isEmpty() || "all".equals(rarity);
    }

    // 为当前库存构造“关联档位”冗余数据，用来快速检查每个收藏品是否有上/下级产物池。
    public NextTierCatalogResponse loadNextTierCatalog(NextTierCatalogRequest request) throws Exception {
        return loadNextTierCatalog(buffAccountService.resolveDefaultAccountId(), request);
    }

    public NextTierCatalogResponse loadNextTierCatalog(long accountId, NextTierCatalogRequest request) throws Exception {
        buffAccountService.requireAccount(accountId);
        InventorySnapshotRecord snapshot = resolveSnapshot(accountId, request.getSnapshotId());
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
        return persistNextTierCatalog(buffAccountService.resolveDefaultAccountId(), request);
    }

    public PersistNextTierCatalogResponse persistNextTierCatalog(long accountId, NextTierCatalogRequest request) throws Exception {
        NextTierCatalogResponse response = loadNextTierCatalog(accountId, request);
        int itemCount = tradeUpNextTierStoreService.replaceForSnapshot(accountId, response.getSnapshotId().longValue(), response.getGroups());
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

    // 方案生成依赖完整产物池；若 catalog 队列仍有待处理 goods，先阻止计算避免给出伪精确 EV。
    private void assertCatalogSyncComplete(long snapshotId) {
        int discoveredCount = catalogSyncTaskStoreService.countAll(snapshotId);
        int remainingCount = catalogSyncTaskStoreService.countOpen(snapshotId);
        if (discoveredCount > 0 && remainingCount > 0) {
            throw new IllegalArgumentException(ErrorMessages.catalogSyncIncomplete(remainingCount));
        }
    }

    private String contractRarity(BuffItem item) {
        if (item == null) {
            return null;
        }
        return item.getFilterRarity() != null ? item.getFilterRarity() : item.getRarity();
    }

    // 冗余关系优先找上一档基材，上一档不存在时再找下一档产物，兼容不同页面的排查视角。
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
