package com.qyaaaa.cstaihuan.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class BuffSessionImportRequest {
    @NotBlank(message = "cookie 不能为空")
    private String cookie;

    @Size(max = 64, message = "source 长度不能超过 64")
    private String source = "frontend";
}
