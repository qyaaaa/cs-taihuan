CREATE TABLE IF NOT EXISTS trade_up_next_tier_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL COMMENT '来源库存快照ID',
    collection_name VARCHAR(255) NOT NULL COMMENT '本级物品所属收藏品',
    base_rarity VARCHAR(64) NOT NULL COMMENT '本级炼金等级',
    target_rarity VARCHAR(64) NOT NULL COMMENT '上一级炼金等级',
    inventory_count INT NOT NULL COMMENT '当前库存里该组可参与炼金的物品数量',
    skin_name VARCHAR(255) NOT NULL COMMENT '上一级目标饰品名称',
    skin_price DECIMAL(18, 2) NOT NULL COMMENT '上一级目标饰品价格',
    min_float DOUBLE NOT NULL COMMENT '目标饰品最小磨损',
    max_float DOUBLE NOT NULL COMMENT '目标饰品最大磨损',
    created_at BIGINT NOT NULL COMMENT '保存时间戳',
    KEY idx_trade_up_next_tier_snapshot (snapshot_id),
    KEY idx_trade_up_next_tier_group (collection_name, base_rarity, target_rarity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
