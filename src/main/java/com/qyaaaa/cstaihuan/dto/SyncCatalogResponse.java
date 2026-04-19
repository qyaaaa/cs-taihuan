package com.qyaaaa.cstaihuan.dto;

public class SyncCatalogResponse {
    private Long snapshotId;
    private int seedItemCount;
    private int seedGoodsCount;
    private int discoveredGoodsCount;
    private int itemCount;
    private String message;

    public SyncCatalogResponse() {
    }

    public SyncCatalogResponse(Long snapshotId, int seedItemCount, int seedGoodsCount, int discoveredGoodsCount, int itemCount, String message) {
        this.snapshotId = snapshotId;
        this.seedItemCount = seedItemCount;
        this.seedGoodsCount = seedGoodsCount;
        this.discoveredGoodsCount = discoveredGoodsCount;
        this.itemCount = itemCount;
        this.message = message;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public int getSeedItemCount() {
        return seedItemCount;
    }

    public void setSeedItemCount(int seedItemCount) {
        this.seedItemCount = seedItemCount;
    }

    public int getSeedGoodsCount() {
        return seedGoodsCount;
    }

    public void setSeedGoodsCount(int seedGoodsCount) {
        this.seedGoodsCount = seedGoodsCount;
    }

    public int getDiscoveredGoodsCount() {
        return discoveredGoodsCount;
    }

    public void setDiscoveredGoodsCount(int discoveredGoodsCount) {
        this.discoveredGoodsCount = discoveredGoodsCount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
