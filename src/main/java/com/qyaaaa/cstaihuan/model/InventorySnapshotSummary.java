package com.qyaaaa.cstaihuan.model;

public class InventorySnapshotSummary {
    private int itemCount;
    private int tradableCount;
    private int withFloatCount;
    private double totalCost;

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
}
