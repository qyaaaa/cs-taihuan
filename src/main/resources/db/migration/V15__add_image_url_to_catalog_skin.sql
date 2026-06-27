ALTER TABLE catalog_skin
    ADD COLUMN image_url VARCHAR(512) NULL COMMENT 'BUFF 饰品图标 URL，来自 goods_info 的 original_icon_url/icon_url，旧数据为空需重新同步补全' AFTER price;
