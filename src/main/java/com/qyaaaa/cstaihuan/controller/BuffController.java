package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse;
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
    public InventorySnapshotResponse fetch(@RequestBody FetchInventoryRequest request) throws Exception {
        return buffInventoryService.fetchAndSave(request);
    }

    @PostMapping("/load")
    public InventorySnapshotResponse load(@RequestBody InventorySnapshotRequest request) throws Exception {
        return buffInventoryService.loadFromFile(request);
    }
}
