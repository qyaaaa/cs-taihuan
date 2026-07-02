package com.qyaaaa.cstaihuan.dto;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import lombok.Data;

/** 按磨损精估材料市值的请求：指定要精估的库存件 asset_id 列表（通常来自一个方案的输入）。 */
@Data
public class RefineFloatPricesRequest {
    @NotEmpty(message = "assetIds 不能为空")
    @Size(max = 30, message = "单次最多精估 30 件（每件一个 BUFF 请求，防限流）")
    private List<String> assetIds;
}
