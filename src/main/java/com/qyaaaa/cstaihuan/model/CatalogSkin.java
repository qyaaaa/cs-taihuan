package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
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
    @JsonProperty("image_url")
    private String imageUrl;

    public CatalogSkin(String name, String collection, String rarity, double minFloat, double maxFloat, double price) {
        this.name = name;
        this.collection = collection;
        this.rarity = rarity;
        this.minFloat = minFloat;
        this.maxFloat = maxFloat;
        this.price = price;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity == null ? null : rarity.trim().toLowerCase();
    }

    public void setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey == null ? null : categoryKey.trim().toLowerCase();
    }
}
