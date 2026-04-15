package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.TradeUpOptimizer;
import com.qyaaaa.cstaihuan.config.TradeUpProperties;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TradeUpApplicationService {
    private final InventoryFileService inventoryFileService;
    private final CatalogService catalogService;
    private final TradeUpProperties tradeUpProperties;

    public TradeUpApplicationService(InventoryFileService inventoryFileService, CatalogService catalogService, TradeUpProperties tradeUpProperties) {
        this.inventoryFileService = inventoryFileService;
        this.catalogService = catalogService;
        this.tradeUpProperties = tradeUpProperties;
    }

    public OptimizeTradeUpResponse optimize(OptimizeTradeUpRequest request) throws Exception {
        if (!StringUtils.hasText(request.getInventoryPath())) {
            throw new IllegalArgumentException("inventoryPath is required.");
        }
        if (!StringUtils.hasText(request.getCatalogPath())) {
            throw new IllegalArgumentException("catalogPath is required.");
        }

        List<BuffItem> inventory = inventoryFileService.load(Paths.get(request.getInventoryPath()));
        List<CatalogSkin> catalog = catalogService.load(Paths.get(request.getCatalogPath()));

        double saleFeeRate = request.getSaleFeeRate() == null ? tradeUpProperties.getSaleFeeRate() : request.getSaleFeeRate().doubleValue();
        int maxItemsPerRarity = request.getMaxItemsPerRarity() == null ? tradeUpProperties.getMaxItemsPerRarity() : request.getMaxItemsPerRarity().intValue();
        int maxCombinations = request.getMaxCombinations() == null ? tradeUpProperties.getMaxCombinations() : request.getMaxCombinations().intValue();
        int topK = request.getTopK() == null ? 5 : request.getTopK().intValue();

        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog, saleFeeRate);
        List<TradeUpPlan> plans = optimizer.findBestContracts(
            optimizer.enrichInventory(inventory),
            topK,
            maxItemsPerRarity,
            maxCombinations
        );
        return new OptimizeTradeUpResponse(plans);
    }
}

