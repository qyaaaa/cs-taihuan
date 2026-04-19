ALTER TABLE catalog_skin
    ADD COLUMN goods_id VARCHAR(64) NULL COMMENT 'BUFF 市场 goods_id，用于从库存快照递归补抓目录详情' AFTER name,
    ADD COLUMN category_key VARCHAR(128) NULL COMMENT 'BUFF 物品类目标识，通常来自 tags.category.internal_name，示例：weapon_famas' AFTER rarity,
    ADD COLUMN quality_label VARCHAR(128) NULL COMMENT '前端展示用的中文品质文案，通常来自 tags.rarity.localized_name，示例：隐秘' AFTER category_key;

ALTER TABLE catalog_skin
    ADD UNIQUE KEY uk_catalog_skin_goods_id (goods_id);
