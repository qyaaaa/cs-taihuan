package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.util.List;

public class NextTierCatalogGroup {
    private String collection;
    private String baseRarity;
    private String targetRarity;
    private int inventoryCount;
    private List<CatalogSkin> items;

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getBaseRarity() {
        return baseRarity;
    }

    public void setBaseRarity(String baseRarity) {
        this.baseRarity = baseRarity;
    }

    public String getTargetRarity() {
        return targetRarity;
    }

    public void setTargetRarity(String targetRarity) {
        this.targetRarity = targetRarity;
    }

    public int getInventoryCount() {
        return inventoryCount;
    }

    public void setInventoryCount(int inventoryCount) {
        this.inventoryCount = inventoryCount;
    }

    public List<CatalogSkin> getItems() {
        return items;
    }

    public void setItems(List<CatalogSkin> items) {
        this.items = items;
    }
}
