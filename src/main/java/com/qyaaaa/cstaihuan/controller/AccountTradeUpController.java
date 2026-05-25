package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.FloatCalculationRequest;
import com.qyaaaa.cstaihuan.dto.FloatCalculationResponse;
import com.qyaaaa.cstaihuan.dto.FloatTargetOption;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogRequest;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.dto.PersistNextTierCatalogResponse;
import com.qyaaaa.cstaihuan.service.FloatCalculationService;
import com.qyaaaa.cstaihuan.service.TradeUpApplicationService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/accounts/{accountId}/trade-up")
public class AccountTradeUpController {
    private final TradeUpApplicationService tradeUpApplicationService;
    private final FloatCalculationService floatCalculationService;

    public AccountTradeUpController(TradeUpApplicationService tradeUpApplicationService, FloatCalculationService floatCalculationService) {
        this.tradeUpApplicationService = tradeUpApplicationService;
        this.floatCalculationService = floatCalculationService;
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

    @GetMapping("/float/targets")
    public List<FloatTargetOption> floatTargets(@PathVariable long accountId, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "40") int limit) {
        return floatCalculationService.searchTargets(keyword, limit);
    }

    @PostMapping("/float/calculate")
    public FloatCalculationResponse calculateFloat(@PathVariable long accountId, @Valid @RequestBody FloatCalculationRequest request) {
        return floatCalculationService.calculate(request);
    }
}
