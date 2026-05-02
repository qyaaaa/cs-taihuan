package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
public class NextTierCatalogRequest {
    @Positive(message = ErrorMessages.SNAPSHOT_ID_POSITIVE)
    private Long snapshotId;
}
