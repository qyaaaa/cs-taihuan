package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BuffItem {
    @JsonProperty("asset_id")
    private String assetId;
    private String name;
    private double price;
    // 按磨损精估的市值（该件 float 对应的挂单最低价）；空则回退磨损档价。
    @JsonProperty("float_price")
    private Double floatPrice;
    // 该件所在磨损档的目录底价（展示用，和精估价对照）；enrich 时填充，不落库。
    @JsonProperty("base_price")
    private Double basePrice;
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
        return withCatalog(skin, price);
    }

    // 与 withCatalog 相同，但覆盖价格：用于按素材磨损档目录价计价，而不是按整皮库存地板价计价。
    public BuffItem withCatalog(CatalogSkin skin, double newPrice) {
        BuffItem copy = new BuffItem(assetId, name, newPrice, floatValue, floatValueRaw, imageUrl, wearName, skin.getCollection(), skin.getRarity(), categoryKey, filterRarity, qualityLabel, tradable, goodsId, raw);
        copy.setFloatPrice(floatPrice);
        copy.setBasePrice(basePrice);
        return copy;
    }

    public BuffItem withPrice(double newPrice) {
        BuffItem copy = new BuffItem(assetId, name, newPrice, floatValue, floatValueRaw, imageUrl, wearName, collection, rarity, categoryKey, filterRarity, qualityLabel, tradable, goodsId, raw);
        copy.setFloatPrice(floatPrice);
        copy.setBasePrice(basePrice);
        return copy;
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

    public void setRaw(Map<String, Object> raw) {
        this.raw = raw == null ? new LinkedHashMap<String, Object>() : raw;
    }
}
