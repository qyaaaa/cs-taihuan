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
}
