-- “EV 可能不完整”应只针对从未成功同步过的 goods，而不是 1h/12h 缓存过期后被重新入队刷新的 goods。
-- 任务被 enqueue 重新打回 PENDING 时会丢失“曾成功”状态，这里用独立标记位记录，enqueue 不会重置它。
ALTER TABLE catalog_sync_task
    ADD COLUMN succeeded_once TINYINT NOT NULL DEFAULT 0 COMMENT '是否曾成功同步过（用于判断目录覆盖是否完整，刷新重入队不重置）';

-- 回填：当前已成功的任务直接标记。
UPDATE catalog_sync_task SET succeeded_once = 1 WHERE status = 'SUCCEEDED';

-- 回填：曾成功但已被刷新打回 PENDING 的任务——只要其 goods 已落在 catalog 里，即视为曾覆盖过。
UPDATE catalog_sync_task t
SET t.succeeded_once = 1
WHERE t.succeeded_once = 0
  AND EXISTS (SELECT 1 FROM catalog_skin c WHERE c.goods_id = t.goods_id);
