package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
public class OptimizeTradeUpRequest {
    @Positive(message = "snapshotId 必须大于 0")
    private Long snapshotId;

    @Min(value = 1, message = "topK 不能小于 1")
    @Max(value = 50, message = "topK 不能大于 50")
    private Integer topK;

    @DecimalMin(value = "0.0", inclusive = true, message = "saleFeeRate 不能小于 0")
    @DecimalMax(value = "0.99", inclusive = true, message = "saleFeeRate 不能大于 0.99")
    private Double saleFeeRate;

    @Min(value = 1, message = "maxItemsPerRarity 不能小于 1")
    @Max(value = 1000, message = "maxItemsPerRarity 不能大于 1000")
    private Integer maxItemsPerRarity;

    @Min(value = 1, message = "maxCombinations 不能小于 1")
    @Max(value = 1000000, message = "maxCombinations 不能大于 1000000")
    private Integer maxCombinations;
}
