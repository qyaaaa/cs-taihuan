package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class FetchInventoryRequest {
    @NotBlank(message = ErrorMessages.OUTPUT_PATH_NOT_BLANK)
    private String outputPath;

    @Size(max = 32, message = ErrorMessages.GAME_SIZE)
    private String game;

    @Min(value = 1, message = ErrorMessages.PAGE_SIZE_MIN)
    @Max(value = 200, message = ErrorMessages.PAGE_SIZE_MAX)
    private Integer pageSize;

    @Min(value = 1, message = ErrorMessages.MAX_PAGES_MIN)
    @Max(value = 1000, message = ErrorMessages.MAX_PAGES_MAX)
    private Integer maxPages;

    private String cookie;
    private Boolean forceRefresh;
}
