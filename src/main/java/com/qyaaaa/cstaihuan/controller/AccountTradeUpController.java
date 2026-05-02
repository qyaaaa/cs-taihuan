package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.NextTierCatalogRequest;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.dto.PersistNextTierCatalogResponse;
import com.qyaaaa.cstaihuan.service.TradeUpApplicationService;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/accounts/{accountId}/trade-up")
public class AccountTradeUpController {
    private final TradeUpApplicationService tradeUpApplicationService;

    public AccountTradeUpController(TradeUpApplicationService tradeUpApplicationService) {
        this.tradeUpApplicationService = tradeUpApplicationService;
    }

    @PostMapping("/optimize")
    public OptimizeTradeUpResponse optimize(@PathVariable long accountId, @Valid @RequestBody OptimizeTradeUpRequest request) throws Exception {
        return tradeUpApplicationService.optimize(accountId, request);
    }

    @PostMapping("/next-tier")
    public NextTierCatalogResponse nextTier(@PathVariable long accountId, @Valid @RequestBody NextTierCatalogRequest request) throws Exception {
        return tradeUpApplicationService.loadNextTierCatalog(accountId, request);
    }

    @PostMapping("/next-tier/persist")
    public PersistNextTierCatalogResponse persistNextTier(@PathVariable long accountId, @Valid @RequestBody NextTierCatalogRequest request) throws Exception {
        return tradeUpApplicationService.persistNextTierCatalog(accountId, request);
    }
}
