package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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

    public TradeUpPlan() {
    }

    public TradeUpPlan(String rarity, double inputCost, double expectedOutputValue, double expectedProfit, double roi, double averageInputFloat, List<BuffItem> inputs, List<Outcome> outcomes) {
        this.rarity = rarity;
        this.inputCost = inputCost;
        this.expectedOutputValue = expectedOutputValue;
        this.expectedProfit = expectedProfit;
        this.roi = roi;
        this.averageInputFloat = averageInputFloat;
        this.inputs = inputs;
        this.outcomes = outcomes;
    }

    public String getRarity() {
        return rarity;
    }

    public double getInputCost() {
        return inputCost;
    }

    public double getExpectedOutputValue() {
        return expectedOutputValue;
    }

    public double getExpectedProfit() {
        return expectedProfit;
    }

    public double getRoi() {
        return roi;
    }

    public double getAverageInputFloat() {
        return averageInputFloat;
    }

    public List<BuffItem> getInputs() {
        return inputs;
    }

    public List<Outcome> getOutcomes() {
        return outcomes;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public void setInputCost(double inputCost) {
        this.inputCost = inputCost;
    }

    public void setExpectedOutputValue(double expectedOutputValue) {
        this.expectedOutputValue = expectedOutputValue;
    }

    public void setExpectedProfit(double expectedProfit) {
        this.expectedProfit = expectedProfit;
    }

    public void setRoi(double roi) {
        this.roi = roi;
    }

    public void setAverageInputFloat(double averageInputFloat) {
        this.averageInputFloat = averageInputFloat;
    }

    public void setInputs(List<BuffItem> inputs) {
        this.inputs = inputs;
    }

    public void setOutcomes(List<Outcome> outcomes) {
        this.outcomes = outcomes;
    }
}
