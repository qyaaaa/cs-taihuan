package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptimizeTradeUpResponse {
    private List<TradeUpPlan> plans;
}
