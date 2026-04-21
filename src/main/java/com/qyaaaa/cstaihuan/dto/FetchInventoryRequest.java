package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class FetchInventoryRequest {
    private String outputPath;
    private String game;
    private Integer pageSize;
    private Integer maxPages;
    private String cookie;
    private Boolean forceRefresh;
}
