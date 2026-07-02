package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CatalogSyncTaskMapper extends BaseMapper<CatalogSyncTaskRecord> {
    int enqueue(CatalogSyncTaskRecord record);

    List<CatalogSyncTaskRecord> selectClaimable(@Param("snapshotId") long snapshotId,
                                                @Param("pendingStatus") String pendingStatus,
                                                @Param("failedStatus") String failedStatus,
                                                @Param("maxRetryCount") int maxRetryCount);

    int markClaimed(@Param("id") long id,
                    @Param("processingStatus") String processingStatus,
                    @Param("pendingStatus") String pendingStatus,
                    @Param("failedStatus") String failedStatus,
                    @Param("now") long now);

    int resetProcessing(@Param("snapshotId") long snapshotId,
                        @Param("pendingStatus") String pendingStatus,
                        @Param("processingStatus") String processingStatus,
                        @Param("failureReason") String failureReason,
                        @Param("now") long now);

    int markSucceeded(@Param("id") long id, @Param("succeededStatus") String succeededStatus, @Param("now") long now);

    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("failureReason") String failureReason, @Param("now") long now);

    int countAll(@Param("snapshotId") long snapshotId);

    int countOpen(@Param("snapshotId") long snapshotId,
                  @Param("pendingStatus") String pendingStatus,
                  @Param("processingStatus") String processingStatus,
                  @Param("failedStatus") String failedStatus,
                  @Param("maxRetryCount") int maxRetryCount);

    int countNeverSucceededOpen(@Param("snapshotId") long snapshotId,
                                @Param("pendingStatus") String pendingStatus,
                                @Param("processingStatus") String processingStatus,
                                @Param("failedStatus") String failedStatus,
                                @Param("maxRetryCount") int maxRetryCount);

    int countBySnapshotAndStatus(@Param("snapshotId") long snapshotId, @Param("status") String status);

    List<String> selectRecentlyCompletedGoodsIds(@Param("snapshotId") long snapshotId,
                                                 @Param("succeededStatus") String succeededStatus,
                                                 @Param("skippedStatus") String skippedStatus,
                                                 @Param("attemptedAfter") long attemptedAfter);
}
