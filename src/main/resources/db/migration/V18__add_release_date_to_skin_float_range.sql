ALTER TABLE skin_float_range
    ADD COLUMN release_date VARCHAR(10) NULL COMMENT '所属收藏品上线日期(YYYY-MM-DD)，来自 ByMykel crates 的 first_sale_date；老地图收藏品上游无日期为空' AFTER image_url;
