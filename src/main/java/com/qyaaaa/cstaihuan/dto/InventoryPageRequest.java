package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class InventoryPageRequest {
    @Positive(message = "snapshotId 必须大于 0")
    private Long snapshotId;

    @Size(max = 32, message = "game 长度不能超过 32")
    private String game;

    @Min(value = 1, message = "page 不能小于 1")
    private Integer page;

    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 200, message = "pageSize 不能大于 200")
    private Integer pageSize;

    @Pattern(regexp = "all|consumer|industrial|mil-spec|restricted|classified|covert", message = "rarity 不支持")
    private String rarity;
}
