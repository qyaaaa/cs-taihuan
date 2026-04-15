package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpRequest;
import com.qyaaaa.cstaihuan.dto.OptimizeTradeUpResponse;
import com.qyaaaa.cstaihuan.service.TradeUpApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade-up")
public class TradeUpController {
    private final TradeUpApplicationService tradeUpApplicationService;

    public TradeUpController(TradeUpApplicationService tradeUpApplicationService) {
        this.tradeUpApplicationService = tradeUpApplicationService;
    }

    @PostMapping("/optimize")
    public OptimizeTradeUpResponse optimize(@RequestBody OptimizeTradeUpRequest request) throws Exception {
        return tradeUpApplicationService.optimize(request);
    }
}

