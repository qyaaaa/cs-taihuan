package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse;
import com.qyaaaa.cstaihuan.model.BuffItem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BuffInventoryService {
    private final BuffProperties buffProperties;
    private final BuffApiClient buffApiClient;
    private final InventoryFileService inventoryFileService;
    private final BuffSessionService buffSessionService;

    public BuffInventoryService(BuffProperties buffProperties, BuffApiClient buffApiClient, InventoryFileService inventoryFileService, BuffSessionService buffSessionService) {
        this.buffProperties = buffProperties;
        this.buffApiClient = buffApiClient;
        this.inventoryFileService = inventoryFileService;
        this.buffSessionService = buffSessionService;
    }

    public InventorySnapshotResponse fetchAndSave(FetchInventoryRequest request) throws Exception {
        String outputPath = request.getOutputPath();
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException("outputPath is required.");
        }

        String cookie = buffSessionService.resolveCookie(request.getCookie());

        String game = StringUtils.hasText(request.getGame()) ? request.getGame() : buffProperties.getGame();
        int pageSize = request.getPageSize() == null ? buffProperties.getPageSize() : request.getPageSize().intValue();
        Integer maxPages = request.getMaxPages();

        List<BuffItem> items = buffApiClient.fetchInventory(
            buffProperties.getBaseUrl(),
            cookie,
            game,
            pageSize,
            maxPages
        );

        Path path = Paths.get(outputPath);
        inventoryFileService.save(path, items);
        return new InventorySnapshotResponse(items.size(), path.toString(), items);
    }

    public InventorySnapshotResponse loadFromFile(InventorySnapshotRequest request) throws Exception {
        if (!StringUtils.hasText(request.getInventoryPath())) {
            throw new IllegalArgumentException("inventoryPath is required.");
        }

        Path path = Paths.get(request.getInventoryPath());
        List<BuffItem> items = inventoryFileService.load(path);
        return new InventorySnapshotResponse(items.size(), path.toString(), items);
    }
}
