package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageResponse;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotSummary;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BuffInventoryService {
    private static final Logger log = LoggerFactory.getLogger(BuffInventoryService.class);
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
        return fetchAndSave(request, false);
    }

    public InventorySnapshotResponse forceUpdate(FetchInventoryRequest request) throws Exception {
        return fetchAndSave(request, true);
    }

    private InventorySnapshotResponse fetchAndSave(FetchInventoryRequest request, boolean forceUpdate) throws Exception {
        String outputPath = request.getOutputPath();
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException("outputPath is required.");
        }

        // 先解析这次请求实际要使用的登录态，优先复用后端托管的会话。
        String cookie = buffSessionService.resolveCookie(request.getCookie());

        String game = StringUtils.hasText(request.getGame()) ? request.getGame() : buffProperties.getGame();
        int pageSize = request.getPageSize() == null ? buffProperties.getPageSize() : request.getPageSize().intValue();
        Integer maxPages = request.getMaxPages();
        Path path = Paths.get(outputPath);
        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(game);

        boolean forceRefresh = (forceUpdate || request.getForceRefresh() == null)
            ? true
            : request.getForceRefresh().booleanValue();
        log.info("Fetch inventory started, game={}, outputPath={}, pageSize={}, maxPages={}, forceRefresh={}, latestSnapshotId={}",
            game, path, Integer.valueOf(pageSize), maxPages, Boolean.valueOf(forceRefresh),
            latest.isPresent() ? Long.valueOf(latest.get().getId()) : null);

        // 非强制刷新时，命中冷却窗口就直接复用本地快照，避免重复请求 BUFF。
        if (!forceRefresh && latest.isPresent() && isWithinCooldown(latest.get())) {
            List<BuffItem> cachedItems = inventorySnapshotStoreService.loadItems(latest.get().getId());
            inventorySnapshotStoreService.touch(latest.get().getId());
            inventoryFileService.save(path, cachedItems);
            log.info("Fetch inventory hit cooldown cache, snapshotId={}, itemCount={}",
                Long.valueOf(latest.get().getId()), Integer.valueOf(cachedItems.size()));
            return buildResponse(latest.get(), path, cachedItems, true, "CACHE", "命中本地快照，已跳过重复调用 BUFF 接口。");
        }

        List<BuffItem> items;
        try {
            // 这里拉的是 BUFF 返回的完整库存，后面只会从中筛出武器类物品入库。
            items = buffApiClient.fetchInventory(
                buffProperties.getBaseUrl(),
                cookie,
                game,
                pageSize,
                maxPages
            );
        } catch (BuffRateLimitException ex) {
            // 如果 BUFF 限流但本地已有快照，就优先回退到最近一次成功同步的数据。
            if (latest.isPresent()) {
                List<BuffItem> cachedItems = inventorySnapshotStoreService.loadItems(latest.get().getId());
                inventorySnapshotStoreService.touch(latest.get().getId());
                inventoryFileService.save(path, cachedItems);
                log.warn("Fetch inventory fell back to cached snapshot because of BUFF rate limit, snapshotId={}, itemCount={}",
                    Long.valueOf(latest.get().getId()), Integer.valueOf(cachedItems.size()));
                return buildResponse(latest.get(), path, cachedItems, true, "CACHE", "BUFF 当前限流，已回退到数据库里最近一次保存的库存快照。");
            }
            log.warn("Fetch inventory failed because of BUFF rate limit and no cached snapshot was available");
            throw ex;
        }

        // 本地 json 保留完整库存，数据库和接口返回则统一只保留武器类物品。
        List<BuffItem> persistedItems = filterPersistedItems(items);
        log.info("Fetch inventory parsed result, totalItemCount={}, persistedItemCount={}",
            Integer.valueOf(items.size()), Integer.valueOf(persistedItems.size()));
        String fingerprint = buildFingerprint(persistedItems);
        // 指纹只基于武器类物品计算；没变化就说明本次无需重复落库。
        if (!forceUpdate && latest.isPresent() && fingerprint.equals(latest.get().getFingerprint())) {
            inventorySnapshotStoreService.touch(latest.get().getId());
            inventoryFileService.save(path, items);
            log.info("Fetch inventory reused existing snapshot, snapshotId={}, persistedItemCount={}",
                Long.valueOf(latest.get().getId()), Integer.valueOf(persistedItems.size()));
            return buildResponse(latest.get(), path, persistedItems, false, "REUSED", "远端库存无变化，已复用已有快照记录。");
        }

        InventorySnapshotRecord saved = inventorySnapshotStoreService.saveSnapshot(game, fingerprint, persistedItems);
        // 文件落完整库存，数据库只落筛选后的武器类物品，两个出口各自服务不同用途。
        inventoryFileService.save(path, items);
        log.info("Fetch inventory saved new snapshot, snapshotId={}, persistedItemCount={}, forceUpdate={}",
            Long.valueOf(saved.getId()), Integer.valueOf(persistedItems.size()), Boolean.valueOf(forceUpdate));
        return buildResponse(saved, path, persistedItems, false, "REMOTE",
            forceUpdate ? "已强制从 BUFF 抓取库存并重新写入数据库。" : "已从 BUFF 抓取库存并写入数据库。");
    }

    public InventorySnapshotResponse loadFromFile(InventorySnapshotRequest request) throws Exception {
        if (!StringUtils.hasText(request.getInventoryPath())) {
            throw new IllegalArgumentException("inventoryPath is required.");
        }

        Path path = Paths.get(request.getInventoryPath());
        List<BuffItem> items = filterPersistedItems(inventoryFileService.load(path));
        log.info("Loaded inventory from file, path={}, filteredItemCount={}", path, Integer.valueOf(items.size()));
        return new InventorySnapshotResponse(null, items.size(), path.toString(), items, false, "FILE", null, "已从本地文件载入库存。");
    }

    public InventoryPageResponse loadPage(InventoryPageRequest request) {
        String game = StringUtils.hasText(request.getGame()) ? request.getGame() : buffProperties.getGame();
        InventorySnapshotRecord snapshot = resolveSnapshot(request.getSnapshotId(), game);
        int page = request.getPage() == null || request.getPage().intValue() < 1 ? 1 : request.getPage().intValue();
        int pageSize = request.getPageSize() == null || request.getPageSize().intValue() < 1 ? 50 : request.getPageSize().intValue();

        InventorySnapshotSummary summary = inventorySnapshotStoreService.summarizeSnapshot(snapshot.getId());
        InventoryPageResponse response = new InventoryPageResponse();
        response.setSnapshotId(Long.valueOf(snapshot.getId()));
        response.setItemCount(summary.getItemCount());
        response.setTradableCount(summary.getTradableCount());
        response.setWithFloatCount(summary.getWithFloatCount());
        response.setTotalCost(summary.getTotalCost());
        response.setTotalItems(inventorySnapshotStoreService.countItems(snapshot.getId()));
        response.setCurrentPage(page);
        response.setPageSize(pageSize);
        response.setItems(inventorySnapshotStoreService.loadPagedItems(snapshot.getId(), page, pageSize));
        response.setFetchedAt(formatTimestamp(snapshot.getCreatedAt()));
        log.info("Loaded inventory page, snapshotId={}, page={}, pageSize={}, totalItems={}, returnedItems={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(page), Integer.valueOf(pageSize),
            Integer.valueOf(response.getTotalItems()), Integer.valueOf(response.getItems().size()));
        return response;
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
                .append(safe(item.getCategoryKey())).append('|')
                .append(safe(item.getFilterRarity())).append('|')
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

    public List<BuffItem> filterPersistedItems(List<BuffItem> items) {
        List<BuffItem> filtered = new ArrayList<BuffItem>();
        for (BuffItem item : items) {
            if (isWeaponItem(item)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean isWeaponItem(BuffItem item) {
        return item != null
            && item.getCategoryKey() != null
            && item.getCategoryKey().startsWith("weapon_");
    }

    private InventorySnapshotRecord resolveSnapshot(Long snapshotId, String game) {
        if (snapshotId != null) {
            Optional<InventorySnapshotRecord> snapshot = inventorySnapshotStoreService.findById(snapshotId.longValue());
            if (!snapshot.isPresent()) {
                throw new IllegalArgumentException("Inventory snapshot was not found: " + snapshotId);
            }
            return snapshot.get();
        }
        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(game);
        if (!latest.isPresent()) {
            throw new IllegalArgumentException("No persisted inventory snapshot was found.");
        }
        return latest.get();
    }
}
