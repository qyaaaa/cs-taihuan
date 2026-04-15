package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.List;

public class InventorySnapshotResponse {
    private int itemCount;
    private String outputPath;
    private List<BuffItem> items;

    public InventorySnapshotResponse() {
    }

    public InventorySnapshotResponse(int itemCount, String outputPath, List<BuffItem> items) {
        this.itemCount = itemCount;
        this.outputPath = outputPath;
        this.items = items;
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
}

