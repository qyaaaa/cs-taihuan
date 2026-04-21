package com.qyaaaa.cstaihuan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
