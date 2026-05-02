package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.AsyncTaskResponse;
import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageResponse;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse;
import com.qyaaaa.cstaihuan.service.AsyncTaskService;
import com.qyaaaa.cstaihuan.service.BuffInventoryService;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/accounts/{accountId}/inventory")
public class AccountBuffInventoryController {
    private final BuffInventoryService buffInventoryService;
    private final AsyncTaskService asyncTaskService;

    public AccountBuffInventoryController(BuffInventoryService buffInventoryService, AsyncTaskService asyncTaskService) {
        this.buffInventoryService = buffInventoryService;
        this.asyncTaskService = asyncTaskService;
    }

    @PostMapping("/fetch/task")
    public AsyncTaskResponse fetchTask(@PathVariable final long accountId, @Valid @RequestBody final FetchInventoryRequest request) {
        return asyncTaskService.submit("INVENTORY_FETCH", "库存抓取任务已创建。", new AsyncTaskService.AsyncTaskWork() {
            public Object run(AsyncTaskService.TaskProgress progress) throws Exception {
                return buffInventoryService.fetchAndSaveAsync(accountId, request, progress);
            }
        });
    }

    @PostMapping("/fetch/force/task")
    public AsyncTaskResponse forceFetchTask(@PathVariable final long accountId, @Valid @RequestBody final FetchInventoryRequest request) {
        return asyncTaskService.submit("INVENTORY_FORCE_FETCH", "强制库存抓取任务已创建。", new AsyncTaskService.AsyncTaskWork() {
            public Object run(AsyncTaskService.TaskProgress progress) throws Exception {
                return buffInventoryService.forceUpdateAsync(accountId, request, progress);
            }
        });
    }

    @PostMapping("/page")
    public InventoryPageResponse page(@PathVariable long accountId, @Valid @RequestBody InventoryPageRequest request) {
        return buffInventoryService.loadPage(accountId, request);
    }

    @PostMapping("/fetch")
    public InventorySnapshotResponse fetch(@PathVariable long accountId, @Valid @RequestBody FetchInventoryRequest request) throws Exception {
        return buffInventoryService.fetchAndSave(accountId, request);
    }
}
