package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
public class SyncCatalogRequest {
    @Positive(message = "snapshotId 必须大于 0")
    private Long snapshotId;

    @Min(value = 1, message = "maxDetailRequests 不能小于 1")
    @Max(value = 200, message = "maxDetailRequests 不能大于 200")
    private Integer maxDetailRequests;
}
