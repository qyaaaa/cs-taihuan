package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CatalogSkin {
    private String name;
    @JsonProperty("goods_id")
    private String goodsId;
    private String collection;
    private String rarity;
    @JsonProperty("category_key")
    private String categoryKey;
    @JsonProperty("quality_label")
    private String qualityLabel;
    @JsonProperty("min_float")
    private double minFloat;
    @JsonProperty("max_float")
    private double maxFloat;
    private double price;

    public CatalogSkin() {
    }

    public CatalogSkin(String name, String collection, String rarity, double minFloat, double maxFloat, double price) {
        this.name = name;
        this.collection = collection;
        this.rarity = rarity;
        this.minFloat = minFloat;
        this.maxFloat = maxFloat;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public String getCollection() {
        return collection;
    }

    public String getRarity() {
        return rarity;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public String getQualityLabel() {
        return qualityLabel;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity == null ? null : rarity.trim().toLowerCase();
    }

    public double getMinFloat() {
        return minFloat;
    }

    public void setMinFloat(double minFloat) {
        this.minFloat = minFloat;
    }

    public double getMaxFloat() {
        return maxFloat;
    }

    public void setMaxFloat(double maxFloat) {
        this.maxFloat = maxFloat;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey == null ? null : categoryKey.trim().toLowerCase();
    }

    public void setQualityLabel(String qualityLabel) {
        this.qualityLabel = qualityLabel;
    }
}
