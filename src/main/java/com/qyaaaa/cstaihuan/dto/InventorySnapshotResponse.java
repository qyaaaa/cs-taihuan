package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.List;

public class InventorySnapshotResponse {
    private Long snapshotId;
    private int itemCount;
    private String outputPath;
    private List<BuffItem> items;
    private boolean cacheHit;
    private String dataSource;
    private String fetchedAt;
    private String message;

    public InventorySnapshotResponse() {
    }

    public InventorySnapshotResponse(int itemCount, String outputPath, List<BuffItem> items) {
        this.itemCount = itemCount;
        this.outputPath = outputPath;
        this.items = items;
    }

    public InventorySnapshotResponse(Long snapshotId, int itemCount, String outputPath, List<BuffItem> items, boolean cacheHit, String dataSource, String fetchedAt, String message) {
        this.snapshotId = snapshotId;
        this.itemCount = itemCount;
        this.outputPath = outputPath;
        this.items = items;
        this.cacheHit = cacheHit;
        this.dataSource = dataSource;
        this.fetchedAt = fetchedAt;
        this.message = message;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public List<BuffItem> getItems() {
        return items;
    }

    public void setItems(List<BuffItem> items) {
        this.items = items;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
