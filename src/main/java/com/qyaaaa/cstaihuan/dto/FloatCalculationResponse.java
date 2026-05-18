package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class FloatCalculationResponse {
    private String targetGoodsId;
    private String targetName;
    private String targetCollection;
    private String targetRarity;
    private String targetQualityLabel;
    private double targetFloat;
    private double targetMinFloat;
    private double targetMaxFloat;
    private int contractSize;
    private double requiredAverageInputFloat;
    private double requiredTotalInputFloat;
    private double lockedFloatSum;
    private int lockedSlotCount;
    private int remainingSlotCount;
    private Double requiredRemainingAverageFloat;
    private Double allowedRemainingMinFloat;
    private Double allowedRemainingMaxFloat;
    private boolean reachable;
    private String message;
}
