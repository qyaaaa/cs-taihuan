package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.List;
import lombok.Data;

@Data
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
}
