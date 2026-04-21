package com.qyaaaa.cstaihuan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistNextTierCatalogResponse {
    private Long snapshotId;
    private int groupCount;
    private int itemCount;
    private String message;
}
