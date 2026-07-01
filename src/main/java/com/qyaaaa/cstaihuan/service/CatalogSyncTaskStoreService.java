package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSyncTaskStoreService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private static final int MAX_RETRY_COUNT = 5;

    private final JdbcTemplate jdbcTemplate;

    public CatalogSyncTaskStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int enqueue(long snapshotId, Map<String, String> collectionsByGoodsId) {
        return enqueue(1L, snapshotId, collectionsByGoodsId);
    }

    @Transactional
    public int enqueue(long accountId, long snapshotId, Map<String, String> collectionsByGoodsId) {
        if (collectionsByGoodsId == null || collectionsByGoodsId.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (Map.Entry<String, String> entry : collectionsByGoodsId.entrySet()) {
            changed += enqueue(accountId, snapshotId, entry.getKey(), entry.getValue());
        }
        return changed;
    }

    public int enqueue(long snapshotId, String goodsId, String collection) {
        return enqueue(1L, snapshotId, goodsId, collection);
    }

    public int enqueue(long accountId, long snapshotId, String goodsId, String collection) {
        String normalizedGoodsId = normalizeGoodsId(goodsId);
        if (normalizedGoodsId.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        return jdbcTemplate.update(
            "INSERT INTO catalog_sync_task (account_id, snapshot_id, goods_id, collection_name, status, retry_count, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, 0, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "collection_name = COALESCE(NULLIF(VALUES(collection_name), ''), collection_name), " +
                "status = CASE WHEN status = 'PROCESSING' THEN status ELSE 'PENDING' END, " +
                "failure_reason = NULL, " +
                "updated_at = VALUES(updated_at)",
            Long.valueOf(accountId),
            Long.valueOf(snapshotId),
            normalizedGoodsId,
            normalizeCollection(collection),
            STATUS_PENDING,
            Long.valueOf(now),
            Long.valueOf(now)
        );
    }

    public void resetProcessing(long snapshotId) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
            "UPDATE catalog_sync_task SET status = ?, failure_reason = ?, updated_at = ? WHERE snapshot_id = ? AND status = ?",
            STATUS_PENDING,
            "上次同步中断，已重新放回队列。",
            Long.valueOf(now),
            Long.valueOf(snapshotId),
            STATUS_PROCESSING
        );
    }

    @Transactional
    public Optional<CatalogSyncTaskRecord> claimNext(long snapshotId) {
        List<CatalogSyncTaskRecord> rows = jdbcTemplate.query(
            "SELECT id, account_id, snapshot_id, goods_id, collection_name, status, failure_reason, retry_count, last_attempt_at, created_at, updated_at " +
                "FROM catalog_sync_task " +
                "WHERE snapshot_id = ? " +
                "AND (status = ? OR (status = ? AND retry_count < ?)) " +
                "ORDER BY id ASC LIMIT 1",
            (rs, rowNum) -> mapRecord(rs),
            Long.valueOf(snapshotId),
            STATUS_PENDING,
            STATUS_FAILED,
            Integer.valueOf(MAX_RETRY_COUNT)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        CatalogSyncTaskRecord record = rows.get(0);
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update(
            "UPDATE catalog_sync_task SET status = ?, failure_reason = NULL, retry_count = retry_count + 1, last_attempt_at = ?, updated_at = ? WHERE id = ? AND status IN (?, ?)",
            STATUS_PROCESSING,
            Long.valueOf(now),
            Long.valueOf(now),
            Long.valueOf(record.getId()),
            STATUS_PENDING,
            STATUS_FAILED
        );
        if (updated == 0) {
            return Optional.empty();
        }
        record.setStatus(STATUS_PROCESSING);
        record.setRetryCount(record.getRetryCount() + 1);
        record.setLastAttemptAt(Long.valueOf(now));
        record.setUpdatedAt(now);
        record.setFailureReason(null);
        return Optional.of(record);
    }

    public void markSucceeded(long taskId) {
        // 成功即永久标记 succeeded_once，后续刷新把状态打回 PENDING 也不会清掉它。
        jdbcTemplate.update(
            "UPDATE catalog_sync_task SET status = ?, failure_reason = NULL, succeeded_once = 1, updated_at = ? WHERE id = ?",
            STATUS_SUCCEEDED,
            Long.valueOf(System.currentTimeMillis()),
            Long.valueOf(taskId)
        );
    }

    public void markSkipped(long taskId, String reason) {
        updateStatus(taskId, STATUS_SKIPPED, reason);
    }

    public void markFailed(long taskId, String reason) {
        updateStatus(taskId, STATUS_FAILED, reason);
    }

    public void requeue(long taskId, String reason) {
        updateStatus(taskId, STATUS_PENDING, reason);
    }

    public int countAll(long snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog_sync_task WHERE snapshot_id = ?",
            Integer.class,
            Long.valueOf(snapshotId)
        );
        return count == null ? 0 : count.intValue();
    }

    public int countOpen(long snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog_sync_task WHERE snapshot_id = ? AND (status IN (?, ?) OR (status = ? AND retry_count < ?))",
            Integer.class,
            Long.valueOf(snapshotId),
            STATUS_PENDING,
            STATUS_PROCESSING,
            STATUS_FAILED,
            Integer.valueOf(MAX_RETRY_COUNT)
        );
        return count == null ? 0 : count.intValue();
    }

    // 仅统计“从未成功同步过”的待处理 goods，用于判断目录覆盖是否完整（不含 12h 缓存过期后被重新入队刷新的 goods）。
    public int countNeverSucceededOpen(long snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog_sync_task WHERE snapshot_id = ? AND succeeded_once = 0 AND (status IN (?, ?) OR (status = ? AND retry_count < ?))",
            Integer.class,
            Long.valueOf(snapshotId),
            STATUS_PENDING,
            STATUS_PROCESSING,
            STATUS_FAILED,
            Integer.valueOf(MAX_RETRY_COUNT)
        );
        return count == null ? 0 : count.intValue();
    }

    public int countByStatus(long snapshotId, String status) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog_sync_task WHERE snapshot_id = ? AND status = ?",
            Integer.class,
            Long.valueOf(snapshotId),
            status
        );
        return count == null ? 0 : count.intValue();
    }

    // 返回新鲜窗口内已抓取并处理完成（成功或跳过）的 goods，用于本轮跳过重复抓取。
    // 尤其是箱子、下架商品、空响应这类解析不出皮肤且不会落 catalog_skin 的 goods，
    // 否则它们每轮都会复活为 PENDING 并反复抓取，挤占真正待补的队列。
    public java.util.Set<String> loadRecentlyCompletedGoodsIds(long snapshotId, long attemptedAfter) {
        List<String> rows = jdbcTemplate.query(
            "SELECT goods_id FROM catalog_sync_task " +
                "WHERE snapshot_id = ? AND status IN (?, ?) AND last_attempt_at IS NOT NULL AND last_attempt_at >= ?",
            (rs, rowNum) -> rs.getString("goods_id"),
            Long.valueOf(snapshotId),
            STATUS_SUCCEEDED,
            STATUS_SKIPPED,
            Long.valueOf(attemptedAfter)
        );
        return new java.util.LinkedHashSet<String>(rows);
    }

    private void updateStatus(long taskId, String status, String reason) {
        jdbcTemplate.update(
            "UPDATE catalog_sync_task SET status = ?, failure_reason = ?, updated_at = ? WHERE id = ?",
            status,
            trimReason(reason),
            Long.valueOf(System.currentTimeMillis()),
            Long.valueOf(taskId)
        );
    }

    private CatalogSyncTaskRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        CatalogSyncTaskRecord record = new CatalogSyncTaskRecord();
        record.setId(rs.getLong("id"));
        record.setAccountId(rs.getLong("account_id"));
        record.setSnapshotId(rs.getLong("snapshot_id"));
        record.setGoodsId(rs.getString("goods_id"));
        record.setCollection(rs.getString("collection_name"));
        record.setStatus(rs.getString("status"));
        record.setFailureReason(rs.getString("failure_reason"));
        record.setRetryCount(rs.getInt("retry_count"));
        long lastAttemptAt = rs.getLong("last_attempt_at");
        record.setLastAttemptAt(rs.wasNull() ? null : Long.valueOf(lastAttemptAt));
        record.setCreatedAt(rs.getLong("created_at"));
        record.setUpdatedAt(rs.getLong("updated_at"));
        return record;
    }

    private String normalizeGoodsId(String goodsId) {
        return goodsId == null ? "" : goodsId.trim();
    }

    private String normalizeCollection(String collection) {
        return collection == null ? null : collection.trim();
    }

    private String trimReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() <= 1024) {
            return normalized;
        }
        return normalized.substring(0, 1024);
    }
}
