CREATE TABLE IF NOT EXISTS catalog_sync_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '目录同步队列主键',
    snapshot_id BIGINT NOT NULL COMMENT '库存快照 ID，同一次同步队列按快照隔离',
    goods_id VARCHAR(64) NOT NULL COMMENT 'BUFF goods_id，队列中的最小同步单位',
    collection_name VARCHAR(255) NULL COMMENT '从库存或商品详情推导出的收藏品名称，用于解析目录数据',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING 待处理，PROCESSING 处理中，SUCCEEDED 成功，FAILED 失败，SKIPPED 跳过',
    failure_reason VARCHAR(1024) NULL COMMENT '最近一次失败或跳过原因',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已尝试次数，每次 claim 任务时递增',
    last_attempt_at BIGINT NULL COMMENT '最近一次开始处理时间，Unix 毫秒',
    created_at BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒',
    updated_at BIGINT NOT NULL COMMENT '更新时间，Unix 毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalog_sync_task_snapshot_goods (snapshot_id, goods_id),
    KEY idx_catalog_sync_task_snapshot_status (snapshot_id, status, id),
    KEY idx_catalog_sync_task_snapshot_updated (snapshot_id, updated_at)
) COMMENT='BUFF 目录同步持久化队列表';
