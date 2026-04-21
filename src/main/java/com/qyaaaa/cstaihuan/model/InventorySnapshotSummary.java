package com.qyaaaa.cstaihuan.model;

import lombok.Data;

@Data
public class InventorySnapshotSummary {
    private int itemCount;
    private int tradableCount;
    private int withFloatCount;
    private double totalCost;
}
