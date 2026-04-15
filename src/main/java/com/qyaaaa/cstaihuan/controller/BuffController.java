package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.FetchInventoryResponse;
import com.qyaaaa.cstaihuan.service.BuffInventoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buff/inventory")
public class BuffController {
    private final BuffInventoryService buffInventoryService;

    public BuffController(BuffInventoryService buffInventoryService) {
        this.buffInventoryService = buffInventoryService;
    }

    @PostMapping("/fetch")
    public FetchInventoryResponse fetch(@RequestBody FetchInventoryRequest request) throws Exception {
        return buffInventoryService.fetchAndSave(request);
    }
}

