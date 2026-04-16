ALTER TABLE inventory_item
    ADD COLUMN image_url VARCHAR(1024) NULL AFTER float_value,
    ADD COLUMN wear_name VARCHAR(128) NULL AFTER image_url,
    ADD COLUMN quality_label VARCHAR(128) NULL AFTER rarity;
