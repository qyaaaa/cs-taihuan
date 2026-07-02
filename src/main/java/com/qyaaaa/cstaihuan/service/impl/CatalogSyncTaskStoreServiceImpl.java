package com.qyaaaa.cstaihuan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qyaaaa.cstaihuan.mapper.CatalogSyncTaskMapper;
import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import com.qyaaaa.cstaihuan.service.CatalogSyncTaskStoreService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSyncTaskStoreServiceImpl extends ServiceImpl<CatalogSyncTaskMapper, CatalogSyncTaskRecord> implements CatalogSyncTaskStoreService {
    private static final int MAX_RETRY_COUNT = 5;

    @Override
    @Transactional
    public int enqueue(long snapshotId, Map<String, String> collectionsByGoodsId) {
        return enqueue(1L, snapshotId, collectionsByGoodsId);
    }

    /**
     * 批量入队按 goods_id 去重；已存在任务只刷新状态和集合名，避免重复抓取同一商品详情。
     */
    @Override
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

    @Override
    public int enqueue(long snapshotId, String goodsId, String collection) {
        return enqueue(1L, snapshotId, goodsId, collection);
    }

    @Override
    public int enqueue(long accountId, long snapshotId, String goodsId, String collection) {
        String normalizedGoodsId = normalizeGoodsId(goodsId);
        if (normalizedGoodsId.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        CatalogSyncTaskRecord record = new CatalogSyncTaskRecord();
        record.setAccountId(accountId);
        record.setSnapshotId(snapshotId);
        record.setGoodsId(normalizedGoodsId);
        record.setCollection(normalizeCollection(collection));
        record.setStatus(STATUS_PENDING);
        record.setRetryCount(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return baseMapper.enqueue(record);
    }

    @Override
    public void resetProcessing(long snapshotId) {
        baseMapper.resetProcessing(snapshotId, STATUS_PENDING, STATUS_PROCESSING, "上次同步中断，已重新放回队列。", System.currentTimeMillis());
    }

    /**
     * 抢占下一条可同步任务时使用状态条件更新，避免多个 worker 同时拿到同一条任务。
     */
    @Override
    @Transactional
    public Optional<CatalogSyncTaskRecord> claimNext(long snapshotId) {
        List<CatalogSyncTaskRecord> rows = baseMapper.selectClaimable(snapshotId, STATUS_PENDING, STATUS_FAILED, MAX_RETRY_COUNT);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        CatalogSyncTaskRecord record = rows.get(0);
        long now = System.currentTimeMillis();
        int updated = baseMapper.markClaimed(record.getId(), STATUS_PROCESSING, STATUS_PENDING, STATUS_FAILED, now);
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

    @Override
    public void markSucceeded(long taskId) {
        // 成功即永久标记 succeeded_once，后续刷新把状态打回 PENDING 也不会清掉它。
        baseMapper.markSucceeded(taskId, STATUS_SUCCEEDED, System.currentTimeMillis());
    }

    @Override
    public void markSkipped(long taskId, String reason) {
        updateStatus(taskId, STATUS_SKIPPED, reason);
    }

    @Override
    public void markFailed(long taskId, String reason) {
        updateStatus(taskId, STATUS_FAILED, reason);
    }

    @Override
    public void requeue(long taskId, String reason) {
        updateStatus(taskId, STATUS_PENDING, reason);
    }

    @Override
    public int countAll(long snapshotId) {
        return baseMapper.countAll(snapshotId);
    }

    @Override
    public int countOpen(long snapshotId) {
        return baseMapper.countOpen(snapshotId, STATUS_PENDING, STATUS_PROCESSING, STATUS_FAILED, MAX_RETRY_COUNT);
    }

    // 仅统计“从未成功同步过”的待处理 goods，用于判断目录覆盖是否完整（不含 12h 缓存过期后被重新入队刷新的 goods）。
    @Override
    public int countNeverSucceededOpen(long snapshotId) {
        return baseMapper.countNeverSucceededOpen(snapshotId, STATUS_PENDING, STATUS_PROCESSING, STATUS_FAILED, MAX_RETRY_COUNT);
    }

    @Override
    public int countByStatus(long snapshotId, String status) {
        return baseMapper.countBySnapshotAndStatus(snapshotId, status);
    }

    // 返回新鲜窗口内已抓取并处理完成（成功或跳过）的 goods，用于本轮跳过重复抓取。
    // 尤其是箱子、下架商品、空响应这类解析不出皮肤且不会落 catalog_skin 的 goods，
    // 否则它们每轮都会复活为 PENDING 并反复抓取，挤占真正待补的队列。
    @Override
    public Set<String> loadRecentlyCompletedGoodsIds(long snapshotId, long attemptedAfter) {
        List<String> rows = baseMapper.selectRecentlyCompletedGoodsIds(snapshotId, STATUS_SUCCEEDED, STATUS_SKIPPED, attemptedAfter);
        return new LinkedHashSet<String>(rows);
    }

    private void updateStatus(long taskId, String status, String reason) {
        baseMapper.updateStatus(taskId, status, trimReason(reason), System.currentTimeMillis());
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
