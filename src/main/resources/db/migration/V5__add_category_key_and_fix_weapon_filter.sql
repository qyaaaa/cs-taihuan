ALTER TABLE inventory_item
    ADD COLUMN category_key VARCHAR(128) NULL COMMENT 'BUFF 物品类目标识，通常来自 tags.category.internal_name，示例：weapon_famas、weapon_ak47' AFTER rarity;

UPDATE inventory_item
SET category_key = COALESCE(
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.tags.category.internal_name')),
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.tags.weapon.internal_name'))
)
WHERE category_key IS NULL;

ALTER TABLE inventory_item
    MODIFY COLUMN category_key VARCHAR(128) NULL COMMENT 'BUFF 物品类目标识，通常来自 tags.category.internal_name，示例：weapon_famas、weapon_ak47',
    MODIFY COLUMN filter_rarity VARCHAR(64) NULL COMMENT '标准化稀有度，保留用于炼金等级分析，不再作为库存过滤条件';
