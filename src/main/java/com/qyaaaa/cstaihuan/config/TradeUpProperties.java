package com.qyaaaa.cstaihuan.config;

import com.qyaaaa.cstaihuan.model.FloatPriceBand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade-up")
@Data
public class TradeUpProperties {
    private double saleFeeRate = 0.025d;
    private int maxItemsPerRarity = 18;
    private int maxCombinations = 25000;
    private Map<String, Double> outputPriceFactors = new LinkedHashMap<String, Double>();
    private Map<String, List<FloatPriceBand>> outputPriceBands = new LinkedHashMap<String, List<FloatPriceBand>>();
}
