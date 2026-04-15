package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CatalogSkin {
    private String name;
    private String collection;
    private String rarity;
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

    public String getCollection() {
        return collection;
    }

    public String getRarity() {
        return rarity;
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

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
