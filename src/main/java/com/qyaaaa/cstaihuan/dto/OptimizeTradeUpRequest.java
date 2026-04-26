package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
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

    @Pattern(regexp = "expectedOutputValue|expectedProfit|roi|inputCost|rarityRank", message = "sortBy 不支持")
    private String sortBy;

    @Pattern(regexp = "all|consumer|industrial|mil-spec|restricted|classified|covert|gold", message = "rarity 不支持")
    private String rarity;

    @Pattern(regexp = "all|normal|stattrak", message = "trackType 不支持")
    private String trackType;

    @Pattern(regexp = "all|regular|gold", message = "contractType 不支持")
    private String contractType;
}
