package com.qyaaaa.cstaihuan.dto;

public class PersistNextTierCatalogResponse {
    private Long snapshotId;
    private int groupCount;
    private int itemCount;
    private String message;

    public PersistNextTierCatalogResponse() {
    }

    public PersistNextTierCatalogResponse(Long snapshotId, int groupCount, int itemCount, String message) {
        this.snapshotId = snapshotId;
        this.groupCount = groupCount;
        this.itemCount = itemCount;
        this.message = message;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
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
