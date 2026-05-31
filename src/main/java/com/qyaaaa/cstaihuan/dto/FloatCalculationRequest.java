package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import java.util.List;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class FloatCalculationRequest {
    // Either targetGoodsId (BUFF catalog) or targetName (float-range library) must be provided.
    private String targetGoodsId;

    // Used when the target only exists in the float-range library (no BUFF goods_id).
    private String targetName;

    @NotNull(message = ErrorMessages.TARGET_FLOAT_REQUIRED)
    @DecimalMin(value = "0.0", inclusive = true, message = ErrorMessages.TARGET_FLOAT_MIN)
    @DecimalMax(value = "1.0", inclusive = true, message = ErrorMessages.TARGET_FLOAT_MAX)
    private Double targetFloat;

    @NotNull(message = ErrorMessages.CONTRACT_SIZE_REQUIRED)
    @Min(value = 5, message = ErrorMessages.CONTRACT_SIZE_UNSUPPORTED)
    @Max(value = 10, message = ErrorMessages.CONTRACT_SIZE_UNSUPPORTED)
    private Integer contractSize;

    @Size(max = 10, message = ErrorMessages.LOCKED_INPUT_FLOAT_SIZE)
    private List<@DecimalMin(value = "0.0", inclusive = true, message = ErrorMessages.LOCKED_INPUT_FLOAT_MIN) @DecimalMax(value = "1.0", inclusive = true, message = ErrorMessages.LOCKED_INPUT_FLOAT_MAX) Double> lockedInputFloats;
}
