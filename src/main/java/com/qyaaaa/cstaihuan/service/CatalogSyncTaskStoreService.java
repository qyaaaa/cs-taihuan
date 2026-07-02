package com.qyaaaa.cstaihuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CatalogSyncTaskStoreService extends IService<CatalogSyncTaskRecord> {
    String STATUS_PENDING = "PENDING";
    String STATUS_PROCESSING = "PROCESSING";
    String STATUS_SUCCEEDED = "SUCCEEDED";
    String STATUS_FAILED = "FAILED";
    String STATUS_SKIPPED = "SKIPPED";

    int enqueue(long snapshotId, Map<String, String> collectionsByGoodsId);

    int enqueue(long accountId, long snapshotId, Map<String, String> collectionsByGoodsId);

    int enqueue(long snapshotId, String goodsId, String collection);

    int enqueue(long accountId, long snapshotId, String goodsId, String collection);

    void resetProcessing(long snapshotId);

    Optional<CatalogSyncTaskRecord> claimNext(long snapshotId);

    void markSucceeded(long taskId);

    void markSkipped(long taskId, String reason);

    void markFailed(long taskId, String reason);

    void requeue(long taskId, String reason);

    int countAll(long snapshotId);

    int countOpen(long snapshotId);

    int countNeverSucceededOpen(long snapshotId);

    int countByStatus(long snapshotId, String status);

    Set<String> loadRecentlyCompletedGoodsIds(long snapshotId, long attemptedAfter);
}
