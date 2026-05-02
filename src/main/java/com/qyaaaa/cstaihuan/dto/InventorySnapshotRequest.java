package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventorySnapshotRequest {
    @NotBlank(message = ErrorMessages.INVENTORY_PATH_NOT_BLANK)
    private String inventoryPath;
}
