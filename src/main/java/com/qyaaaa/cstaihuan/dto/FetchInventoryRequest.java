package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class FetchInventoryRequest {
    @NotBlank(message = "outputPath 不能为空")
    private String outputPath;

    @Size(max = 32, message = "game 长度不能超过 32")
    private String game;

    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 200, message = "pageSize 不能大于 200")
    private Integer pageSize;

    @Min(value = 1, message = "maxPages 不能小于 1")
    @Max(value = 1000, message = "maxPages 不能大于 1000")
    private Integer maxPages;

    private String cookie;
    private Boolean forceRefresh;
}
