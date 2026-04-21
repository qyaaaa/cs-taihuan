ALTER TABLE catalog_skin
    MODIFY COLUMN rarity VARCHAR(64) NOT NULL COMMENT '标准化后的炼金档位，例如 mil-spec、restricted、classified、covert、gold';

ALTER TABLE inventory_item
    MODIFY COLUMN filter_rarity VARCHAR(64) NULL COMMENT '用于库存接口过滤和炼金筛选的标准化稀有度，示例：classified、covert、gold';

