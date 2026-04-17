ALTER TABLE inventory_item
    ADD COLUMN float_value_raw VARCHAR(64) NULL AFTER float_value;
