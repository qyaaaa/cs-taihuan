CREATE TABLE IF NOT EXISTS buff_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL COMMENT '本地账号昵称',
    buff_user_id VARCHAR(128) NULL COMMENT 'BUFF 用户 ID，当前可为空',
    masked_cookie VARCHAR(64) NULL COMMENT '脱敏 Cookie，用于列表展示',
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/VALID/INVALID',
    last_validated_at VARCHAR(64) NULL COMMENT '最近一次校验时间',
    created_at BIGINT NOT NULL COMMENT '创建时间戳',
    updated_at BIGINT NOT NULL COMMENT '更新时间戳',
    KEY idx_buff_account_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地 BUFF 账号';

INSERT INTO buff_account (id, nickname, buff_user_id, masked_cookie, status, last_validated_at, created_at, updated_at)
SELECT 1, '默认账号', NULL, NULL, 'UNKNOWN', NULL, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
WHERE NOT EXISTS (SELECT 1 FROM buff_account WHERE id = 1);

CREATE TABLE IF NOT EXISTS buff_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL COMMENT '所属本地账号',
    cookie_text LONGTEXT NOT NULL COMMENT 'BUFF Cookie 明文，仅限本机使用',
    source VARCHAR(64) NULL COMMENT '导入来源',
    created_at VARCHAR(64) NOT NULL COMMENT '创建时间',
    updated_at VARCHAR(64) NOT NULL COMMENT '更新时间',
    last_validated_at VARCHAR(64) NULL COMMENT '最近一次校验时间',
    UNIQUE KEY uk_buff_session_account (account_id),
    CONSTRAINT fk_buff_session_account
        FOREIGN KEY (account_id) REFERENCES buff_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按账号隔离的 BUFF 会话';

ALTER TABLE inventory_snapshot
    ADD COLUMN account_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属本地账号' AFTER id,
    ADD KEY idx_inventory_snapshot_account_game_created_at (account_id, game, created_at);

ALTER TABLE catalog_sync_task
    ADD COLUMN account_id BIGINT NOT NULL DEFAULT 1 COMMENT '触发同步的本地账号' AFTER id,
    DROP INDEX uk_catalog_sync_task_snapshot_goods,
    ADD UNIQUE KEY uk_catalog_sync_task_account_snapshot_goods (account_id, snapshot_id, goods_id),
    ADD KEY idx_catalog_sync_task_account_snapshot_status (account_id, snapshot_id, status, id);

ALTER TABLE trade_up_next_tier_item
    ADD COLUMN account_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属本地账号' AFTER id,
    ADD KEY idx_trade_up_next_tier_account_snapshot (account_id, snapshot_id);
