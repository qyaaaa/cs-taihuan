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
    @JsonProperty("float_value_raw")
    private String floatValueRaw;
    @JsonProperty("image_url")
    private String imageUrl;
    @JsonProperty("wear_name")
    private String wearName;
    private String collection;
    private String rarity;
    @JsonProperty("category_key")
    private String categoryKey;
    @JsonProperty("filter_rarity")
    private String filterRarity;
    @JsonProperty("quality_label")
    private String qualityLabel;
    private boolean tradable = true;
    @JsonProperty("goods_id")
    private String goodsId;
    private Map<String, Object> raw = new LinkedHashMap<String, Object>();

    public BuffItem() {
    }

    public BuffItem(String assetId, String name, double price, Double floatValue, String floatValueRaw, String imageUrl, String wearName, String collection, String rarity, String categoryKey, String filterRarity, String qualityLabel, boolean tradable, String goodsId, Map<String, Object> raw) {
        this.assetId = assetId;
        this.name = name;
        this.price = price;
        this.floatValue = floatValue;
        this.floatValueRaw = floatValueRaw;
        this.imageUrl = imageUrl;
        this.wearName = wearName;
        this.collection = collection;
        this.rarity = rarity;
        this.categoryKey = categoryKey;
        this.filterRarity = filterRarity;
        this.qualityLabel = qualityLabel;
        this.tradable = tradable;
        this.goodsId = goodsId;
        this.raw = raw == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(raw);
    }

    public BuffItem withCatalog(CatalogSkin skin) {
        return new BuffItem(assetId, name, price, floatValue, floatValueRaw, imageUrl, wearName, skin.getCollection(), skin.getRarity(), categoryKey, filterRarity, qualityLabel, tradable, goodsId, raw);
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

    public String getFloatValueRaw() {
        return floatValueRaw;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getWearName() {
        return wearName;
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

    public String getFilterRarity() {
        return filterRarity;
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

    public void setFloatValueRaw(String floatValueRaw) {
        this.floatValueRaw = floatValueRaw;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setWearName(String wearName) {
        this.wearName = wearName;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity == null ? null : rarity.trim().toLowerCase();
    }

    public void setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey == null ? null : categoryKey.trim().toLowerCase();
    }

    public void setFilterRarity(String filterRarity) {
        this.filterRarity = filterRarity == null ? null : filterRarity.trim().toLowerCase();
    }

    public void setQualityLabel(String qualityLabel) {
        this.qualityLabel = qualityLabel;
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
