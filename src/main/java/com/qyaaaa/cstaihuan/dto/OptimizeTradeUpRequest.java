package com.qyaaaa.cstaihuan.dto;

public class OptimizeTradeUpRequest {
    private Long snapshotId;
    private Integer topK;
    private Double saleFeeRate;
    private Integer maxItemsPerRarity;
    private Integer maxCombinations;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
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
