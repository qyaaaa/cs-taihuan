ALTER TABLE inventory_item
    ADD COLUMN float_price DECIMAL(18, 2) NULL COMMENT '按磨损精估的市值：该件 float 对应的 BUFF 挂单最低价（档内低磨损有溢价），空表示未精估、回退档价' AFTER price;
