package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.AsyncTaskResponse;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.service.AsyncTaskService;
import com.qyaaaa.cstaihuan.service.CatalogApplicationService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    // Backfills outcome-tier skins (from the inventory's collections) that the user doesn't own,
    // so trade-up outcome pools are complete and expected values aren't inflated by missing skins.
    @PostMapping("/backfill-outcomes")
    public Map<String, Object> backfillOutcomes(@PathVariable long accountId,
            @RequestParam(value = "maxSkinSearches", required = false) Integer maxSkinSearches,
            @RequestParam(value = "collection", required = false) String collection) throws Exception {
        return catalogApplicationService.backfillOutcomeCatalog(accountId, maxSkinSearches, collection);
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
