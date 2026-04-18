ALTER TABLE inventory_item
    ADD COLUMN filter_rarity VARCHAR(64) NULL COMMENT '用于库存接口过滤和炼金筛选的标准化稀有度，示例：covert、classified、restricted' AFTER rarity;

UPDATE inventory_item
SET filter_rarity = CASE
    WHEN rarity IN ('ancient_weapon', 'covert_weapon', 'covert grade', 'covert') THEN 'covert'
    WHEN rarity IN ('legendary_weapon', 'classified_weapon', 'classified grade', 'classified') THEN 'classified'
    WHEN rarity IN ('mythical_weapon', 'restricted_weapon', 'restricted grade', 'restricted') THEN 'restricted'
    WHEN rarity IN ('rare_weapon', 'milspec_weapon', 'milspec', 'mil-spec grade', 'mil-spec') THEN 'mil-spec'
    WHEN rarity IN ('common_weapon', 'industrial_weapon', 'industrial grade', 'industrial') THEN 'industrial'
    WHEN rarity IN ('default_weapon', 'consumer_weapon', 'consumer grade', 'consumer') THEN 'consumer'
    ELSE NULL
END
WHERE filter_rarity IS NULL;

ALTER TABLE inventory_item
    MODIFY COLUMN rarity VARCHAR(64) NULL COMMENT 'BUFF 原始稀有度标识，通常来自 tags.rarity.internal_name，示例：ancient_weapon、milspec_weapon',
    MODIFY COLUMN filter_rarity VARCHAR(64) NULL COMMENT '用于库存接口过滤和炼金筛选的标准化稀有度，示例：covert、classified、restricted',
    MODIFY COLUMN quality_label VARCHAR(128) NULL COMMENT '前端展示用的中文品质文案，通常来自 tags.rarity.localized_name，示例：隐秘',
    MODIFY COLUMN float_value_raw VARCHAR(64) NULL COMMENT 'BUFF 返回的原始磨损字符串，保留完整精度';
