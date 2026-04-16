package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BuffInventoryService {
    private final BuffProperties buffProperties;
    private final BuffApiClient buffApiClient;
    private final InventoryFileService inventoryFileService;
    private final BuffSessionService buffSessionService;
    private final InventorySnapshotStoreService inventorySnapshotStoreService;

    public BuffInventoryService(BuffProperties buffProperties, BuffApiClient buffApiClient, InventoryFileService inventoryFileService, BuffSessionService buffSessionService, InventorySnapshotStoreService inventorySnapshotStoreService) {
        this.buffProperties = buffProperties;
        this.buffApiClient = buffApiClient;
        this.inventoryFileService = inventoryFileService;
        this.buffSessionService = buffSessionService;
        this.inventorySnapshotStoreService = inventorySnapshotStoreService;
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
        Path path = Paths.get(outputPath);
        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(game);

        boolean forceRefresh = request.getForceRefresh() == null ? true : request.getForceRefresh().booleanValue();

        if (!forceRefresh && latest.isPresent() && isWithinCooldown(latest.get())) {
            List<BuffItem> cachedItems = inventorySnapshotStoreService.loadItems(latest.get().getId());
            inventorySnapshotStoreService.touch(latest.get().getId());
            inventoryFileService.save(path, cachedItems);
            return buildResponse(latest.get(), path, cachedItems, true, "CACHE", "命中本地快照，已跳过重复调用 BUFF 接口。");
        }

        List<BuffItem> items = buffApiClient.fetchInventory(
            buffProperties.getBaseUrl(),
            cookie,
            game,
            pageSize,
            maxPages
        );

        String fingerprint = buildFingerprint(items);
        if (latest.isPresent() && fingerprint.equals(latest.get().getFingerprint())) {
            inventorySnapshotStoreService.touch(latest.get().getId());
            inventoryFileService.save(path, items);
            return buildResponse(latest.get(), path, items, false, "REUSED", "远端库存无变化，已复用已有快照记录。");
        }

        InventorySnapshotRecord saved = inventorySnapshotStoreService.saveSnapshot(game, fingerprint, items);
        inventoryFileService.save(path, items);
        return buildResponse(saved, path, items, false, "REMOTE", "已从 BUFF 抓取库存并写入数据库。");
    }

    public InventorySnapshotResponse loadFromFile(InventorySnapshotRequest request) throws Exception {
        if (!StringUtils.hasText(request.getInventoryPath())) {
            throw new IllegalArgumentException("inventoryPath is required.");
        }

        Path path = Paths.get(request.getInventoryPath());
        List<BuffItem> items = inventoryFileService.load(path);
        return new InventorySnapshotResponse(null, items.size(), path.toString(), items, false, "FILE", null, "已从本地文件载入库存。");
    }

    private boolean isWithinCooldown(InventorySnapshotRecord record) {
        long cooldownMillis = Math.max(0L, buffProperties.getFetchCooldownSeconds()) * 1000L;
        if (cooldownMillis == 0L) {
            return false;
        }
        return System.currentTimeMillis() - record.getLastSeenAt() < cooldownMillis;
    }

    private InventorySnapshotResponse buildResponse(InventorySnapshotRecord snapshot, Path outputPath, List<BuffItem> items, boolean cacheHit, String dataSource, String message) {
        return new InventorySnapshotResponse(
            Long.valueOf(snapshot.getId()),
            items.size(),
            outputPath.toString(),
            items,
            cacheHit,
            dataSource,
            formatTimestamp(snapshot.getCreatedAt()),
            message
        );
    }

    private String formatTimestamp(long epochMillis) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC));
    }

    private String buildFingerprint(List<BuffItem> items) {
        List<BuffItem> sorted = new ArrayList<BuffItem>(items);
        Collections.sort(sorted, Comparator.comparing(this::stableKey));
        StringBuilder builder = new StringBuilder();
        for (BuffItem item : sorted) {
            builder.append(safe(item.getAssetId())).append('|')
                .append(safe(item.getGoodsId())).append('|')
                .append(safe(item.getName())).append('|')
                .append(item.getPrice()).append('|')
                .append(item.getFloatValue() == null ? "" : item.getFloatValue()).append('|')
                .append(safe(item.getCollection())).append('|')
                .append(safe(item.getRarity())).append('|')
                .append(item.isTradable())
                .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(value & 0xff)));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private String stableKey(BuffItem item) {
        return safe(item.getAssetId()) + "|" + safe(item.getGoodsId()) + "|" + safe(item.getName());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
