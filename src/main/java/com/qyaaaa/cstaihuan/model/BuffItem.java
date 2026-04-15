package com.qyaaaa.cstaihuan.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuffItem {
    private final String assetId;
    private final String name;
    private final double price;
    private final Double floatValue;
    private final String collection;
    private final String rarity;
    private final boolean tradable;
    private final String goodsId;
    private final Map<String, Object> raw;

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

    public static BuffItem fromMap(Map<String, Object> payload) {
        return new BuffItem(
            stringValue(payload.get("asset_id")),
            stringValue(payload.get("name")),
            doubleValue(payload.get("price"), 0.0d),
            nullableDouble(payload.get("float_value")),
            stringValue(payload.get("collection")),
            normalizeNullable(payload.get("rarity")),
            payload.get("tradable") == null || Boolean.parseBoolean(String.valueOf(payload.get("tradable"))),
            stringValue(payload.get("goods_id")),
            mapValue(payload.get("raw"))
        );
    }

    public BuffItem withCatalog(CatalogSkin skin) {
        return new BuffItem(assetId, name, price, floatValue, skin.getCollection(), skin.getRarity(), tradable, goodsId, raw);
    }

    public static List<Object> toJsonList(List<BuffItem> items) {
        List<Object> rows = new ArrayList<Object>();
        for (BuffItem item : items) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("asset_id", item.assetId);
            row.put("name", item.name);
            row.put("price", item.price);
            row.put("float_value", item.floatValue);
            row.put("collection", item.collection);
            row.put("rarity", item.rarity);
            row.put("tradable", Boolean.valueOf(item.tradable));
            row.put("goods_id", item.goodsId);
            row.put("raw", item.raw);
            rows.add(row);
        }
        return rows;
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

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeNullable(Object value) {
        return value == null ? null : String.valueOf(value).trim().toLowerCase();
    }

    private static double doubleValue(Object value, double defaultValue) {
        return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
    }

    private static Double nullableDouble(Object value) {
        return value == null ? null : Double.valueOf(String.valueOf(value));
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        return new LinkedHashMap<String, Object>();
    }
}

