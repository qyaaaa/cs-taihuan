package com.qyaaaa.cstaihuan.dto;

public class OptimizeTradeUpRequest {
    private String inventoryPath;
    private String catalogPath;
    private Integer topK;
    private Double saleFeeRate;
    private Integer maxItemsPerRarity;
    private Integer maxCombinations;

    public String getInventoryPath() {
        return inventoryPath;
    }

    public void setInventoryPath(String inventoryPath) {
        this.inventoryPath = inventoryPath;
    }

    public String getCatalogPath() {
        return catalogPath;
    }

    public void setCatalogPath(String catalogPath) {
        this.catalogPath = catalogPath;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getSaleFeeRate() {
        return saleFeeRate;
    }

    public void setSaleFeeRate(Double saleFeeRate) {
        this.saleFeeRate = saleFeeRate;
    }

    public Integer getMaxItemsPerRarity() {
        return maxItemsPerRarity;
    }

    public void setMaxItemsPerRarity(Integer maxItemsPerRarity) {
        this.maxItemsPerRarity = maxItemsPerRarity;
    }

    public Integer getMaxCombinations() {
        return maxCombinations;
    }

    public void setMaxCombinations(Integer maxCombinations) {
        this.maxCombinations = maxCombinations;
    }
}

