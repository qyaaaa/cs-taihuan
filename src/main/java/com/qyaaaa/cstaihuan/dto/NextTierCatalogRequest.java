package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.Positive;
import lombok.Data;

@Data
public class NextTierCatalogRequest {
    @Positive(message = "snapshotId 必须大于 0")
    private Long snapshotId;
}
