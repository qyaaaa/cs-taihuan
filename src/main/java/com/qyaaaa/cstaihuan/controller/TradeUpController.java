package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.FloatCalculationRequest;
import com.qyaaaa.cstaihuan.dto.FloatCalculationResponse;
import com.qyaaaa.cstaihuan.dto.FloatTargetOption;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogRequest;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.PersistNextTierCatalogResponse;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.service.FloatCalculationService;
import com.qyaaaa.cstaihuan.service.TradeUpApplicationService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trade-up")
public class TradeUpController {
    private final TradeUpApplicationService tradeUpApplicationService;
    private final FloatCalculationService floatCalculationService;

    public TradeUpController(TradeUpApplicationService tradeUpApplicationService, FloatCalculationService floatCalculationService) {
        this.tradeUpApplicationService = tradeUpApplicationService;
        this.floatCalculationService = floatCalculationService;
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

    @GetMapping("/float/targets")
    public List<FloatTargetOption> floatTargets(@RequestParam(required = false) String collection,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "40") int limit) {
        String effectiveName = (name != null && !name.trim().isEmpty()) ? name : keyword;
        return floatCalculationService.searchTargets(collection, effectiveName, limit);
    }

    @GetMapping("/float/collections")
    public List<java.util.Map<String, String>> floatCollections() {
        return floatCalculationService.listCollections();
    }

    @PostMapping("/float/calculate")
    public FloatCalculationResponse calculateFloat(@Valid @RequestBody FloatCalculationRequest request) {
        return floatCalculationService.calculate(request);
    }
}
