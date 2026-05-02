package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.AsyncTaskResponse;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.service.AsyncTaskService;
import com.qyaaaa.cstaihuan.service.CatalogApplicationService;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/accounts/{accountId}/catalog")
public class AccountCatalogController {
    private final CatalogApplicationService catalogApplicationService;
    private final AsyncTaskService asyncTaskService;

    public AccountCatalogController(CatalogApplicationService catalogApplicationService, AsyncTaskService asyncTaskService) {
        this.catalogApplicationService = catalogApplicationService;
        this.asyncTaskService = asyncTaskService;
    }

    @PostMapping("/sync")
    public SyncCatalogResponse syncCatalog(@PathVariable long accountId, @Valid @RequestBody(required = false) SyncCatalogRequest request) throws Exception {
        return catalogApplicationService.syncCatalog(accountId, request);
    }

    @PostMapping("/sync/task")
    public AsyncTaskResponse syncCatalogTask(@PathVariable final long accountId, @Valid @RequestBody(required = false) final SyncCatalogRequest request) {
        return asyncTaskService.submit("CATALOG_SYNC", "Catalog 同步任务已创建。", new AsyncTaskService.AsyncTaskWork() {
            public Object run(AsyncTaskService.TaskProgress progress) throws Exception {
                return catalogApplicationService.syncCatalogAsync(accountId, request, progress);
            }
        });
    }
}
