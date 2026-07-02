package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("catalog_skin")
public class CatalogSkin {
    @JsonIgnore
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @JsonProperty("goods_id")
    private String goodsId;
    @TableField("collection_name")
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
    @JsonIgnore
    private Long createdAt;
    @JsonIgnore
    private Long updatedAt;

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
