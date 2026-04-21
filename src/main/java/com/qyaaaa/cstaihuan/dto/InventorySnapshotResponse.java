package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventorySnapshotResponse {
    private Long snapshotId;
    private int itemCount;
    private String outputPath;
    private List<BuffItem> items;
    private boolean cacheHit;
    private String dataSource;
    private String fetchedAt;
    private String message;

    public InventorySnapshotResponse(int itemCount, String outputPath, List<BuffItem> items) {
        this.itemCount = itemCount;
        this.outputPath = outputPath;
        this.items = items;
    }
}
