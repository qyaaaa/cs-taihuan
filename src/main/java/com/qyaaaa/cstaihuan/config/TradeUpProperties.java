package com.qyaaaa.cstaihuan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade-up")
public class TradeUpProperties {
    private double saleFeeRate = 0.025d;
    private int maxItemsPerRarity = 18;
    private int maxCombinations = 25000;

    public double getSaleFeeRate() {
        return saleFeeRate;
    }

    public void setSaleFeeRate(double saleFeeRate) {
        this.saleFeeRate = saleFeeRate;
    }

    public int getMaxItemsPerRarity() {
        return maxItemsPerRarity;
    }

    public void setMaxItemsPerRarity(int maxItemsPerRarity) {
        this.maxItemsPerRarity = maxItemsPerRarity;
    }

    public int getMaxCombinations() {
        return maxCombinations;
    }

    public void setMaxCombinations(int maxCombinations) {
        this.maxCombinations = maxCombinations;
    }
}
