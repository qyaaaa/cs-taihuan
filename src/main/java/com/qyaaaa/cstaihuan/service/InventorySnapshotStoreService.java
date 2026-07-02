package com.qyaaaa.cstaihuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotSummary;
import java.util.List;
import java.util.Optional;

public interface InventorySnapshotStoreService extends IService<InventorySnapshotRecord> {
    Optional<InventorySnapshotRecord> findLatest(String game);

    Optional<InventorySnapshotRecord> findLatest(long accountId, String game);

    Optional<InventorySnapshotRecord> findById(long snapshotId);

    Optional<InventorySnapshotRecord> findById(long accountId, long snapshotId);

    List<BuffItem> loadItems(long snapshotId);

    List<BuffItem> loadPagedItems(long snapshotId, int page, int pageSize);

    List<BuffItem> loadPagedItems(long snapshotId, int page, int pageSize, String rarity);

    int countItems(long snapshotId);

    int countItems(long snapshotId, String rarity);

    InventorySnapshotSummary summarizeSnapshot(long snapshotId);

    InventorySnapshotSummary summarizeSnapshot(long snapshotId, String rarity);

    InventorySnapshotRecord saveSnapshot(String game, String fingerprint, List<BuffItem> items);

    InventorySnapshotRecord saveSnapshot(long accountId, String game, String fingerprint, List<BuffItem> items);

    void touch(long snapshotId);

    /** 按磨损精估后写回该件市值；返回受影响行数。 */
    int updateFloatPrice(long snapshotId, String assetId, double floatPrice);

    /** 新快照按 asset_id 结转上一快照的精估价；返回结转行数。 */
    int carryOverFloatPrices(long snapshotId, long prevSnapshotId);

    /** 批量写回一批件的精估价（assetId -> 价），单条 SQL。 */
    int batchUpdateFloatPrices(long snapshotId, java.util.Map<String, Double> pricesByAssetId);
}
