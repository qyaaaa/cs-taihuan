package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class SyncCatalogRequest {
    private Long snapshotId;
    private Integer maxDetailRequests;
}
