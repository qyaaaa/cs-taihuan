package com.qyaaaa.cstaihuan.model;

import java.util.List;

public final class TradeUpPlan {
    private final String rarity;
    private final double inputCost;
    private final double expectedOutputValue;
    private final double expectedProfit;
    private final double roi;
    private final double averageInputFloat;
    private final List<BuffItem> inputs;
    private final List<Outcome> outcomes;

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
}

