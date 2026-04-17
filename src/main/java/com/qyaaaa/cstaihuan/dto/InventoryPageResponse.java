package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.List;

public class InventoryPageResponse {
    private Long snapshotId;
    private int itemCount;
    private int tradableCount;
    private int withFloatCount;
    private double totalCost;
    private int totalItems;
    private int currentPage;
    private int pageSize;
    private List<BuffItem> items;
    private String fetchedAt;

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

    public int getTradableCount() {
        return tradableCount;
    }

    public void setTradableCount(int tradableCount) {
        this.tradableCount = tradableCount;
    }

    public int getWithFloatCount() {
        return withFloatCount;
    }

    public void setWithFloatCount(int withFloatCount) {
        this.withFloatCount = withFloatCount;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<BuffItem> getItems() {
        return items;
    }

    public void setItems(List<BuffItem> items) {
        this.items = items;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
