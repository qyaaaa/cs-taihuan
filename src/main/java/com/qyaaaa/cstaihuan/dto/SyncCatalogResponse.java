package com.qyaaaa.cstaihuan.dto;

public class SyncCatalogResponse {
    private Long snapshotId;
    private int seedItemCount;
    private int seedGoodsCount;
    private int discoveredGoodsCount;
    private int processedGoodsCount;
    private int skippedExistingCount;
    private int remainingGoodsCount;
    private int itemCount;
    private boolean partial;
    private String message;

    public SyncCatalogResponse() {
    }

    public SyncCatalogResponse(Long snapshotId, int seedItemCount, int seedGoodsCount, int discoveredGoodsCount, int processedGoodsCount, int skippedExistingCount, int remainingGoodsCount, int itemCount, boolean partial, String message) {
        this.snapshotId = snapshotId;
        this.seedItemCount = seedItemCount;
        this.seedGoodsCount = seedGoodsCount;
        this.discoveredGoodsCount = discoveredGoodsCount;
        this.processedGoodsCount = processedGoodsCount;
        this.skippedExistingCount = skippedExistingCount;
        this.remainingGoodsCount = remainingGoodsCount;
        this.itemCount = itemCount;
        this.partial = partial;
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

    public int getProcessedGoodsCount() {
        return processedGoodsCount;
    }

    public void setProcessedGoodsCount(int processedGoodsCount) {
        this.processedGoodsCount = processedGoodsCount;
    }

    public int getSkippedExistingCount() {
        return skippedExistingCount;
    }

    public void setSkippedExistingCount(int skippedExistingCount) {
        this.skippedExistingCount = skippedExistingCount;
    }

    public int getRemainingGoodsCount() {
        return remainingGoodsCount;
    }

    public void setRemainingGoodsCount(int remainingGoodsCount) {
        this.remainingGoodsCount = remainingGoodsCount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
