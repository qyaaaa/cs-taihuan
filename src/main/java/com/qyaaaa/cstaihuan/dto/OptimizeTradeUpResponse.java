package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.List;

public class OptimizeTradeUpResponse {
    private List<TradeUpPlan> plans;

    public OptimizeTradeUpResponse() {
    }

    public OptimizeTradeUpResponse(List<TradeUpPlan> plans) {
        this.plans = plans;
    }

    public List<TradeUpPlan> getPlans() {
        return plans;
    }

    public void setPlans(List<TradeUpPlan> plans) {
        this.plans = plans;
    }
}

