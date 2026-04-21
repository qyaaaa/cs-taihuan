package com.qyaaaa.cstaihuan.dto;

import lombok.Data;

@Data
public class BuffSessionImportRequest {
    private String cookie;
    private String source = "frontend";
}
