package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class BuffSessionImportRequest {
    @NotBlank(message = ErrorMessages.COOKIE_NOT_BLANK)
    private String cookie;

    @Size(max = 64, message = ErrorMessages.SOURCE_SIZE)
    private String source = "frontend";
}
