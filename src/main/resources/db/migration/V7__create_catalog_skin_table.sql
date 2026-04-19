CREATE TABLE IF NOT EXISTS catalog_skin (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '饰品英文唯一名，作为 catalog 主匹配键',
    collection_name VARCHAR(255) NOT NULL COMMENT '所属收藏品名称，和库存中的 collection_name 对应',
    rarity VARCHAR(64) NOT NULL COMMENT '标准化后的炼金档位，例如 mil-spec、restricted、classified、covert',
    min_float DOUBLE NOT NULL COMMENT '最小磨损值',
    max_float DOUBLE NOT NULL COMMENT '最大磨损值',
    price DECIMAL(18, 2) NOT NULL COMMENT '目录价格，用于 EV 估算',
    created_at BIGINT NOT NULL COMMENT '创建时间戳',
    updated_at BIGINT NOT NULL COMMENT '更新时间戳',
    UNIQUE KEY uk_catalog_skin_name (name),
    KEY idx_catalog_skin_collection_rarity (collection_name, rarity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='炼金 catalog 全量目录表';
