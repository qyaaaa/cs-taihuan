package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

public class BuffItem {
    @JsonProperty("asset_id")
    private String assetId;
    private String name;
    private double price;
    @JsonProperty("float_value")
    private Double floatValue;
    private String collection;
    private String rarity;
    private boolean tradable = true;
    @JsonProperty("goods_id")
    private String goodsId;
    private Map<String, Object> raw = new LinkedHashMap<String, Object>();

    public BuffItem() {
    }

    public BuffItem(String assetId, String name, double price, Double floatValue, String collection, String rarity, boolean tradable, String goodsId, Map<String, Object> raw) {
        this.assetId = assetId;
        this.name = name;
        this.price = price;
        this.floatValue = floatValue;
        this.collection = collection;
        this.rarity = rarity;
        this.tradable = tradable;
        this.goodsId = goodsId;
        this.raw = raw == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(raw);
    }

    public BuffItem withCatalog(CatalogSkin skin) {
        return new BuffItem(assetId, name, price, floatValue, skin.getCollection(), skin.getRarity(), tradable, goodsId, raw);
    }

    public String getAssetId() {
        return assetId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public Double getFloatValue() {
        return floatValue;
    }

    public String getCollection() {
        return collection;
    }

    public String getRarity() {
        return rarity;
    }

    public boolean isTradable() {
        return tradable;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setFloatValue(Double floatValue) {
        this.floatValue = floatValue;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity == null ? null : rarity.trim().toLowerCase();
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public void setRaw(Map<String, Object> raw) {
        this.raw = raw == null ? new LinkedHashMap<String, Object>() : raw;
    }
}
