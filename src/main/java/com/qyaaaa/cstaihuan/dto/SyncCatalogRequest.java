package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
public class SyncCatalogRequest {
    @Positive(message = ErrorMessages.SNAPSHOT_ID_POSITIVE)
    private Long snapshotId;

    @Min(value = 1, message = ErrorMessages.MAX_DETAIL_REQUESTS_MIN)
    @Max(value = 200, message = ErrorMessages.MAX_DETAIL_REQUESTS_MAX)
    private Integer maxDetailRequests;
}
