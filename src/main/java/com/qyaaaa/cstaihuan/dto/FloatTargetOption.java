package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class FloatTargetOption {
    private String goodsId;
    private String name;
    private String collection;
    private String rarity;
    private String qualityLabel;
    private double minFloat;
    private double maxFloat;
    private double price;
    // catalog 表示来自 BUFF 目录（有 goods_id/价格），library 表示仅来自磨损范围基准库。
    private String floatSource;
}
