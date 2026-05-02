package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class InventoryPageRequest {
    @Positive(message = ErrorMessages.SNAPSHOT_ID_POSITIVE)
    private Long snapshotId;

    @Size(max = 32, message = ErrorMessages.GAME_SIZE)
    private String game;

    @Min(value = 1, message = ErrorMessages.PAGE_MIN)
    private Integer page;

    @Min(value = 1, message = ErrorMessages.PAGE_SIZE_MIN)
    @Max(value = 200, message = ErrorMessages.PAGE_SIZE_MAX)
    private Integer pageSize;

    @Pattern(regexp = "all|consumer|industrial|mil-spec|restricted|classified|covert", message = ErrorMessages.RARITY_UNSUPPORTED)
    private String rarity;
}
