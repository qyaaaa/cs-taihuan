package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventorySnapshotRequest {
    @NotBlank(message = "inventoryPath 不能为空")
    private String inventoryPath;
}
