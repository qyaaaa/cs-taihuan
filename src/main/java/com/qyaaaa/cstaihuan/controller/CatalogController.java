package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.AsyncTaskResponse;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.service.AsyncTaskService;
import com.qyaaaa.cstaihuan.service.CatalogApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogApplicationService catalogApplicationService;
    private final AsyncTaskService asyncTaskService;

    public CatalogController(CatalogApplicationService catalogApplicationService, AsyncTaskService asyncTaskService) {
        this.catalogApplicationService = catalogApplicationService;
        this.asyncTaskService = asyncTaskService;
    }

    @PostMapping("/sync")
    public SyncCatalogResponse syncCatalog(@RequestBody(required = false) SyncCatalogRequest request) throws Exception {
        return catalogApplicationService.syncCatalog(request);
    }

    @PostMapping("/sync/task")
    public AsyncTaskResponse syncCatalogTask(@RequestBody(required = false) SyncCatalogRequest request) {
        return asyncTaskService.submit("CATALOG_SYNC", "Catalog 同步任务已创建。", new AsyncTaskService.AsyncTaskWork() {
            public Object run(AsyncTaskService.TaskProgress progress) throws Exception {
                return catalogApplicationService.syncCatalogAsync(request, progress);
            }
        });
    }

    @PostMapping("/import")
    public SyncCatalogResponse importCatalog(@RequestBody(required = false) SyncCatalogRequest request) throws Exception {
        return catalogApplicationService.syncCatalog(request);
    }
}
