package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OptimizeTradeUpResponse {
    private List<TradeUpPlan> plans;
    private boolean catalogIncomplete;
    private int remainingGoodsCount;
    private String message;

    public OptimizeTradeUpResponse(List<TradeUpPlan> plans) {
        this(plans, false, 0, null);
    }

    public OptimizeTradeUpResponse(List<TradeUpPlan> plans, boolean catalogIncomplete, int remainingGoodsCount, String message) {
        this.plans = plans;
        this.catalogIncomplete = catalogIncomplete;
        this.remainingGoodsCount = remainingGoodsCount;
        this.message = message;
    }
}
