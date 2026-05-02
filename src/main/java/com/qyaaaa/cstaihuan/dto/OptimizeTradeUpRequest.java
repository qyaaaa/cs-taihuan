package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
public class OptimizeTradeUpRequest {
    @Positive(message = ErrorMessages.SNAPSHOT_ID_POSITIVE)
    private Long snapshotId;

    @Min(value = 1, message = ErrorMessages.TOP_K_MIN)
    @Max(value = 50, message = ErrorMessages.TOP_K_MAX)
    private Integer topK;

    @DecimalMin(value = "0.0", inclusive = true, message = ErrorMessages.SALE_FEE_RATE_MIN)
    @DecimalMax(value = "0.99", inclusive = true, message = ErrorMessages.SALE_FEE_RATE_MAX)
    private Double saleFeeRate;

    @Min(value = 1, message = ErrorMessages.MAX_ITEMS_PER_RARITY_MIN)
    @Max(value = 1000, message = ErrorMessages.MAX_ITEMS_PER_RARITY_MAX)
    private Integer maxItemsPerRarity;

    @Min(value = 1, message = ErrorMessages.MAX_COMBINATIONS_MIN)
    @Max(value = 1000000, message = ErrorMessages.MAX_COMBINATIONS_MAX)
    private Integer maxCombinations;

    @Pattern(regexp = "expectedOutputValue|expectedProfit|roi|inputCost|rarityRank", message = ErrorMessages.SORT_BY_UNSUPPORTED)
    private String sortBy;

    @Pattern(regexp = "all|consumer|industrial|mil-spec|restricted|classified|covert|gold", message = ErrorMessages.RARITY_UNSUPPORTED)
    private String rarity;

    @Pattern(regexp = "all|normal|stattrak", message = ErrorMessages.TRACK_TYPE_UNSUPPORTED)
    private String trackType;

    @Pattern(regexp = "all|regular|gold", message = ErrorMessages.CONTRACT_TYPE_UNSUPPORTED)
    private String contractType;
}
