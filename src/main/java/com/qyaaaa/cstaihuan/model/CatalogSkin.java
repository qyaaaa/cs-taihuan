package com.qyaaaa.cstaihuan.model;

import java.util.Map;

public final class CatalogSkin {
    private final String name;
    private final String collection;
    private final String rarity;
    private final double minFloat;
    private final double maxFloat;
    private final double price;

    public CatalogSkin(String name, String collection, String rarity, double minFloat, double maxFloat, double price) {
        this.name = name;
        this.collection = collection;
        this.rarity = rarity;
        this.minFloat = minFloat;
        this.maxFloat = maxFloat;
        this.price = price;
    }

    public static CatalogSkin fromMap(Map<String, Object> payload) {
        return new CatalogSkin(
            String.valueOf(payload.get("name")),
            String.valueOf(payload.get("collection")),
            String.valueOf(payload.get("rarity")).trim().toLowerCase(),
            doubleValue(payload.get("min_float"), 0.0d),
            doubleValue(payload.get("max_float"), 1.0d),
            doubleValue(payload.get("price"), 0.0d)
        );
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

    public double getMinFloat() {
        return minFloat;
    }

    public double getMaxFloat() {
        return maxFloat;
    }

    public double getPrice() {
        return price;
    }

    private static double doubleValue(Object value, double defaultValue) {
        return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
    }
}

