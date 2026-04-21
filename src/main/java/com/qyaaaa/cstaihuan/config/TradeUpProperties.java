package com.qyaaaa.cstaihuan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade-up")
@Data
public class TradeUpProperties {
    private double saleFeeRate = 0.025d;
    private int maxItemsPerRarity = 18;
    private int maxCombinations = 25000;
}
