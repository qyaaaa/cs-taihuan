package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageResponse;
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

    /**
     * 从 BUFF 拉取当前账号库存，并返回只保留武器类物品后的结果。
     *
     * 这条接口本身只负责记录请求日志和转发给服务层，真正的处理逻辑都在
     * {@link com.qyaaaa.cstaihuan.service.BuffInventoryService#fetchAndSave}：
     * 1. 优先使用后端已保存的 BUFF 会话，必要时也支持请求里显式传 cookie
     * 2. 调用 BUFF 库存接口抓取原始库存
     * 3. 将原始库存写回本地 json 文件，便于后续排查和离线复用
     * 4. 只把 category_key 以 weapon_ 开头的武器类物品写入数据库快照
     * 5. 如果命中冷却窗口、库存指纹未变化，或 BUFF 触发 429，则尽量复用已有快照
     *
     * 返回值里的 items 也只包含武器类物品，因此前端和数据库展示口径一致。
    */
    @PostMapping("/fetch")
    public InventorySnapshotResponse fetch(@RequestBody FetchInventoryRequest request) throws Exception {
        return buffInventoryService.fetchAndSave(request);
    }

    @PostMapping("/fetch/force")
    public InventorySnapshotResponse forceFetch(@RequestBody FetchInventoryRequest request) throws Exception {
        return buffInventoryService.forceUpdate(request);
    }

    @PostMapping("/load")
    public InventorySnapshotResponse load(@RequestBody InventorySnapshotRequest request) throws Exception {
        return buffInventoryService.loadFromFile(request);
    }

    @PostMapping("/page")
    public InventoryPageResponse page(@RequestBody InventoryPageRequest request) {
        return buffInventoryService.loadPage(request);
    }
}
