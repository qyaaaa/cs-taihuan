CREATE TABLE IF NOT EXISTS inventory_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game VARCHAR(32) NOT NULL,
    item_count INT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    KEY idx_inventory_snapshot_game_created_at (game, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    asset_id VARCHAR(128) NULL,
    goods_id VARCHAR(128) NULL,
    name VARCHAR(255) NULL,
    price DECIMAL(18, 2) NOT NULL,
    float_value DOUBLE NULL,
    collection_name VARCHAR(255) NULL,
    rarity VARCHAR(64) NULL,
    tradable TINYINT(1) NOT NULL,
    raw_json LONGTEXT NULL,
    KEY idx_inventory_item_snapshot_id (snapshot_id),
    CONSTRAINT fk_inventory_item_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES inventory_snapshot (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
