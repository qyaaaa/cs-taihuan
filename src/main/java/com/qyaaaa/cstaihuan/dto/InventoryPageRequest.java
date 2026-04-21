package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class InventoryPageRequest {
    private Long snapshotId;
    private String game;
    private Integer page;
    private Integer pageSize;
}
