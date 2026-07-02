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
}
