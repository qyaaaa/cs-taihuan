ALTER TABLE skin_float_range
    ADD COLUMN image_url VARCHAR(512) NULL COMMENT '饰品图标 URL，来自快照 image 字段（ByMykel/CSGO-API 按 id 补入），用于收藏品图鉴展示' AFTER collection_zh;
