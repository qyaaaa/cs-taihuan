package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class OptimizeTradeUpRequest {
    private Long snapshotId;
    private Integer topK;
    private Double saleFeeRate;
    private Integer maxItemsPerRarity;
    private Integer maxCombinations;
}
