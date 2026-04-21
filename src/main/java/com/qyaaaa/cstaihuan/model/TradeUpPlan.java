package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeUpPlan {
    private String rarity;
    @JsonProperty("input_cost")
    private double inputCost;
    @JsonProperty("expected_output_value")
    private double expectedOutputValue;
    @JsonProperty("expected_profit")
    private double expectedProfit;
    private double roi;
    @JsonProperty("average_input_float")
    private double averageInputFloat;
    private List<BuffItem> inputs;
    private List<Outcome> outcomes;
}
