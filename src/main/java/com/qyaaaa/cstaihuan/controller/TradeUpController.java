package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.NextTierCatalogRequest;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.PersistNextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.service.TradeUpApplicationService;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trade-up")
public class TradeUpController {
    private final TradeUpApplicationService tradeUpApplicationService;

    public TradeUpController(TradeUpApplicationService tradeUpApplicationService) {
        this.tradeUpApplicationService = tradeUpApplicationService;
    }

    @PostMapping("/optimize")
    public OptimizeTradeUpResponse optimize(@Valid @RequestBody OptimizeTradeUpRequest request) throws Exception {
        return tradeUpApplicationService.optimize(request);
    }

    @PostMapping("/next-tier")
    public NextTierCatalogResponse nextTier(@Valid @RequestBody NextTierCatalogRequest request) throws Exception {
        return tradeUpApplicationService.loadNextTierCatalog(request);
    }

    @PostMapping("/next-tier/persist")
    public PersistNextTierCatalogResponse persistNextTier(@Valid @RequestBody NextTierCatalogRequest request) throws Exception {
        return tradeUpApplicationService.persistNextTierCatalog(request);
    }
}
