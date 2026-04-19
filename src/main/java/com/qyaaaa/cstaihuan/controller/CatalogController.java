package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.service.CatalogApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogApplicationService catalogApplicationService;

    public CatalogController(CatalogApplicationService catalogApplicationService) {
        this.catalogApplicationService = catalogApplicationService;
    }

    @PostMapping("/sync")
    public SyncCatalogResponse syncCatalog(@RequestBody(required = false) SyncCatalogRequest request) throws Exception {
        return catalogApplicationService.syncCatalog(request);
    }

    @PostMapping("/import")
    public SyncCatalogResponse importCatalog(@RequestBody(required = false) SyncCatalogRequest request) throws Exception {
        return catalogApplicationService.syncCatalog(request);
    }
}
