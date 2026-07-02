package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.FetchInventoryRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageRequest;
import com.qyaaaa.cstaihuan.dto.InventoryPageResponse;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotRequest;
import com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
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
    private final BuffAccountService buffAccountService;
    private final InventorySnapshotStoreService inventorySnapshotStoreService;
    private final SkinFloatRangeService skinFloatRangeService;
    // 后台精估专用单线程：拉取任务提交后立即返回，精估被限流时在此线程内冷却续跑，不阻塞其它任务。
    private final java.util.concurrent.ExecutorService floatRefineExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "float-refine");
            t.setDaemon(true);
            return t;
        });
    // 每账号同时只允许一个后台精估任务，避免连续拉取叠加重复线程。
    private final java.util.Set<Long> refineRunningAccounts = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public BuffInventoryService(BuffProperties buffProperties, BuffApiClient buffApiClient, InventoryFileService inventoryFileService, BuffSessionService buffSessionService, BuffAccountService buffAccountService, InventorySnapshotStoreService inventorySnapshotStoreService, SkinFloatRangeService skinFloatRangeService) {
        this.buffProperties = buffProperties;
        this.buffApiClient = buffApiClient;
        this.inventoryFileService = inventoryFileService;
        this.buffSessionService = buffSessionService;
        this.buffAccountService = buffAccountService;
        this.inventorySnapshotStoreService = inventorySnapshotStoreService;
        this.skinFloatRangeService = skinFloatRangeService;
    }

    public InventorySnapshotResponse fetchAndSave(FetchInventoryRequest request) throws Exception {
        return fetchAndSave(buffAccountService.resolveDefaultAccountId(), request, false, null);
    }

    public InventorySnapshotResponse fetchAndSave(long accountId, FetchInventoryRequest request) throws Exception {
        return fetchAndSave(accountId, request, false, null);
    }

    public InventorySnapshotResponse forceUpdate(FetchInventoryRequest request) throws Exception {
        return fetchAndSave(buffAccountService.resolveDefaultAccountId(), request, true, null);
    }

    public InventorySnapshotResponse forceUpdate(long accountId, FetchInventoryRequest request) throws Exception {
        return fetchAndSave(accountId, request, true, null);
    }

    public InventorySnapshotResponse fetchAndSaveAsync(FetchInventoryRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return fetchAndSave(buffAccountService.resolveDefaultAccountId(), request, false, progress);
    }

    public InventorySnapshotResponse fetchAndSaveAsync(long accountId, FetchInventoryRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return fetchAndSave(accountId, request, false, progress);
    }

    public InventorySnapshotResponse forceUpdateAsync(FetchInventoryRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return fetchAndSave(buffAccountService.resolveDefaultAccountId(), request, true, progress);
    }

    public InventorySnapshotResponse forceUpdateAsync(long accountId, FetchInventoryRequest request, AsyncTaskService.TaskProgress progress) throws Exception {
        return fetchAndSave(accountId, request, true, progress);
    }

    private InventorySnapshotResponse fetchAndSave(long accountId, FetchInventoryRequest request, boolean forceUpdate, AsyncTaskService.TaskProgress progress) throws Exception {
        buffAccountService.requireAccount(accountId);
        String outputPath = request.getOutputPath();
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException(ErrorMessages.OUTPUT_PATH_REQUIRED);
        }

        // 先解析这次请求实际要使用的登录态，优先复用后端托管的会话。
        String cookie = buffSessionService.resolveCookie(accountId, request.getCookie());

        String game = StringUtils.hasText(request.getGame()) ? request.getGame() : buffProperties.getGame();
        int pageSize = request.getPageSize() == null ? buffProperties.getPageSize() : request.getPageSize().intValue();
        Integer maxPages = request.getMaxPages();
        Path path = Paths.get(outputPath);
        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(accountId, game);

        boolean forceRefresh = (forceUpdate || request.getForceRefresh() == null)
            ? true
            : request.getForceRefresh().booleanValue();
        log.info("Fetch inventory started, accountId={}, game={}, outputPath={}, pageSize={}, maxPages={}, forceRefresh={}, latestSnapshotId={}",
            Long.valueOf(accountId), game, path, Integer.valueOf(pageSize), maxPages, Boolean.valueOf(forceRefresh),
            latest.isPresent() ? Long.valueOf(latest.get().getId()) : null);
        if (progress != null) {
            progress.update(2, null, maxPages, "库存抓取任务已创建，正在解析 BUFF 会话。");
        }

        // 非强制刷新时，命中冷却窗口就直接复用本地快照，避免重复请求 BUFF。
        if (!forceRefresh && latest.isPresent() && isWithinCooldown(latest.get())) {
            List<BuffItem> cachedItems = inventorySnapshotStoreService.loadItems(latest.get().getId());
            inventorySnapshotStoreService.touch(latest.get().getId());
            inventoryFileService.save(path, cachedItems);
            log.info("Fetch inventory hit cooldown cache, snapshotId={}, itemCount={}",
                Long.valueOf(latest.get().getId()), Integer.valueOf(cachedItems.size()));
            if (progress != null) {
                progress.update(100, Integer.valueOf(cachedItems.size()), Integer.valueOf(cachedItems.size()), "命中本地快照，已跳过 BUFF 请求。");
            }
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
                maxPages,
                progress
            );
        } catch (BuffRateLimitException ex) {
            // 如果 BUFF 限流但本地已有快照，就优先回退到最近一次成功同步的数据。
            if (latest.isPresent()) {
                List<BuffItem> cachedItems = inventorySnapshotStoreService.loadItems(latest.get().getId());
                inventorySnapshotStoreService.touch(latest.get().getId());
                inventoryFileService.save(path, cachedItems);
                log.warn("Fetch inventory fell back to cached snapshot because of BUFF rate limit, snapshotId={}, itemCount={}",
                    Long.valueOf(latest.get().getId()), Integer.valueOf(cachedItems.size()));
                if (progress != null) {
                    progress.update(100, Integer.valueOf(cachedItems.size()), Integer.valueOf(cachedItems.size()), "BUFF 限流，已回退到最近一次数据库快照。");
                }
                return buildResponse(latest.get(), path, cachedItems, true, "CACHE", "BUFF 当前限流，已回退到数据库里最近一次保存的库存快照。");
            }
            log.warn("Fetch inventory failed because of BUFF rate limit and no cached snapshot was available");
            throw ex;
        }

        // 本地 json 保留完整库存，数据库和接口返回则统一只保留武器类物品。
        List<BuffItem> persistedItems = filterPersistedItems(items);
        if (progress != null) {
            progress.update(92, Integer.valueOf(persistedItems.size()), Integer.valueOf(items.size()), "库存抓取完成，正在筛选武器类物品并计算指纹。");
        }
        log.info("Fetch inventory parsed result, totalItemCount={}, persistedItemCount={}",
            Integer.valueOf(items.size()), Integer.valueOf(persistedItems.size()));
        String fingerprint = buildFingerprint(persistedItems);
        // 指纹只基于武器类物品计算；没变化就说明本次无需重复落库。
        if (!forceUpdate && latest.isPresent() && fingerprint.equals(latest.get().getFingerprint())) {
            inventorySnapshotStoreService.touch(latest.get().getId());
            inventoryFileService.save(path, items);
            log.info("Fetch inventory reused existing snapshot, snapshotId={}, persistedItemCount={}",
                Long.valueOf(latest.get().getId()), Integer.valueOf(persistedItems.size()));
            if (progress != null) {
                progress.update(100, Integer.valueOf(persistedItems.size()), Integer.valueOf(persistedItems.size()), "远端库存无变化，已复用已有数据库快照。");
            }
            return buildResponse(latest.get(), path, persistedItems, false, "REUSED", "远端库存无变化，已复用已有快照记录。");
        }

        if (progress != null) {
            progress.update(96, Integer.valueOf(persistedItems.size()), Integer.valueOf(items.size()), "正在写入数据库快照和本地 JSON。");
        }
        InventorySnapshotRecord saved = inventorySnapshotStoreService.saveSnapshot(accountId, game, fingerprint, persistedItems);
        // 文件落完整库存，数据库只落筛选后的武器类物品，两个出口各自服务不同用途。
        inventoryFileService.save(path, items);
        log.info("Fetch inventory saved new snapshot, snapshotId={}, persistedItemCount={}, forceUpdate={}",
            Long.valueOf(saved.getId()), Integer.valueOf(persistedItems.size()), Boolean.valueOf(forceUpdate));

        // 同一件饰品 float 不变：新快照先按 asset_id 结转上一快照的精估价（免费、瞬间）。
        if (latest.isPresent()) {
            int carried = inventorySnapshotStoreService.carryOverFloatPrices(saved.getId(), latest.get().getId());
            log.info("Carried over float prices to new snapshot, snapshotId={}, carried={}", Long.valueOf(saved.getId()), Integer.valueOf(carried));
        }
        // 精估改为独立后台线程：拉取任务立即完成；后台被限流就冷却等待续跑，直到全部回填。方案页按钮做兜底。
        submitBackgroundRefine(accountId, saved.getId(), cookie);
        if (progress != null) {
            progress.update(100, Integer.valueOf(persistedItems.size()), Integer.valueOf(persistedItems.size()), "库存快照已保存。");
        }
        return buildResponse(saved, path, persistedItems, false, "REMOTE",
            forceUpdate ? "已强制从 BUFF 抓取库存并重新写入数据库。" : "已从 BUFF 抓取库存并写入数据库。");
    }

    public InventorySnapshotResponse loadFromFile(InventorySnapshotRequest request) throws Exception {
        if (!StringUtils.hasText(request.getInventoryPath())) {
            throw new IllegalArgumentException(ErrorMessages.INVENTORY_PATH_REQUIRED);
        }

        Path path = Paths.get(request.getInventoryPath());
        List<BuffItem> items = filterPersistedItems(inventoryFileService.load(path));
        log.info("Loaded inventory from file, path={}, filteredItemCount={}", path, Integer.valueOf(items.size()));
        return new InventorySnapshotResponse(null, items.size(), path.toString(), items, false, "FILE", null, "已从本地文件载入库存。");
    }

    /**
     * 按磨损精估指定库存件的市值：对每件查 BUFF 挂单（paintwear ∈ [磨损档下限, 该件 float]）取最低价，
     * 写回 inventory_item.float_price。档内低磨损有溢价，档地板价会低估这类材料，精估后 EV 更准。
     * 每件一个请求、按同步间隔节流；被限流即停并返回部分结果（已写回的保留）。
     */
    public java.util.Map<String, Object> refineFloatPrices(long accountId, List<String> assetIds) {
        buffAccountService.requireAccount(accountId);
        String game = buffProperties.getGame();
        InventorySnapshotRecord snapshot = resolveSnapshot(accountId, null, game);
        String cookie;
        try {
            cookie = buffSessionService.resolveCookie(accountId, null);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("读取 BUFF 会话失败: " + ex.getMessage(), ex);
        }

        java.util.LinkedHashSet<String> uniqueAssetIds = new java.util.LinkedHashSet<String>(assetIds == null ? java.util.Collections.<String>emptyList() : assetIds);
        java.util.Map<String, BuffItem> byAssetId = new java.util.LinkedHashMap<String, BuffItem>();
        for (BuffItem item : inventorySnapshotStoreService.loadItems(snapshot.getId())) {
            if (item.getAssetId() != null) {
                byAssetId.put(item.getAssetId(), item);
            }
        }
        List<BuffItem> targets = new java.util.ArrayList<BuffItem>();
        for (String assetId : uniqueAssetIds) {
            BuffItem item = byAssetId.get(assetId);
            if (item != null) {
                targets.add(item);
            }
        }
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<java.util.Map<String, Object>>();
        int[] result = refineItemsGrouped(snapshot.getId(), cookie, targets, rows, null, uniqueAssetIds.size());

        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<String, Object>();
        summary.put("snapshotId", Long.valueOf(snapshot.getId()));
        summary.put("requested", Integer.valueOf(uniqueAssetIds.size()));
        summary.put("refined", Integer.valueOf(result[0]));
        summary.put("skipped", Integer.valueOf(result[1]));
        summary.put("rateLimited", Boolean.valueOf(result[2] == 1));
        summary.put("items", rows);
        log.info("Refined float prices, accountId={}, snapshotId={}, requested={}, refined={}, skipped={}, rateLimited={}",
            Long.valueOf(accountId), Long.valueOf(snapshot.getId()), Integer.valueOf(uniqueAssetIds.size()),
            Integer.valueOf(result[0]), Integer.valueOf(result[1]), Boolean.valueOf(result[2] == 1));
        return summary;
    }

    /**
     * 精估核心：按 (goods_id, 磨损子桶) 去重后查挂单底价，组内所有件回填同一价。
     * float 落在档尾“底价子桶”的件市值≈档地板价，直接回填档价、不发请求；
     * 其余件每组一次请求（max_paintwear=组内最大 float），大幅减少 BUFF 请求数。
     * 返回 {回填件数, 跳过件数, 是否被限流(0/1), 实际查询次数}。
     */
    private int[] refineItemsGrouped(long snapshotId, String cookie, List<BuffItem> targets,
            java.util.List<java.util.Map<String, Object>> rowsOut, AsyncTaskService.TaskProgress progress, int maxQueries) {
        String game = buffProperties.getGame();
        long interval = Math.max(1000L, buffProperties.getCatalogSync().getRequestIntervalMillis());

        int refined = 0;
        int skipped = 0;
        boolean rateLimited = false;

        // 1) 按 BUFF 官方子区间（固定切点 + 皮肤实际范围裁剪，见 WearSuffix.buffPaintwearSegments）分段：
        //    落在档尾“底价段”的件市值≈档地板价，直接回填；其余按 (goods_id, 段号) 聚组去重。
        java.util.Map<String, double[]> skinRanges = skinFloatRangeService.nameToRange();
        java.util.Map<String, java.util.List<BuffItem>> groups = new java.util.LinkedHashMap<String, java.util.List<BuffItem>>();
        java.util.Map<String, double[]> groupQueryRange = new java.util.LinkedHashMap<String, double[]>();
        for (BuffItem item : targets) {
            if (item == null || item.getFloatValue() == null || item.getGoodsId() == null || item.getGoodsId().trim().isEmpty()) {
                skipped++;
                continue;
            }
            double f = item.getFloatValue().doubleValue();
            double[] skinRange = skinRanges.get(com.qyaaaa.cstaihuan.util.WearSuffix.toRangeMatchKey(item.getName()));
            double[][] segments = com.qyaaaa.cstaihuan.util.WearSuffix.buffPaintwearSegments(
                item.getName(),
                skinRange == null ? null : Double.valueOf(skinRange[0]),
                skinRange == null ? null : Double.valueOf(skinRange[1]));
            int segIndex = segments.length - 1;
            for (int i = 0; i < segments.length; i++) {
                if (f < segments[i][1] || i == segments.length - 1) {
                    segIndex = i;
                    break;
                }
            }
            if (segIndex == segments.length - 1) {
                // 底价段（档尾）：该段挂单最低价就是档地板价，直接回填、不花请求。
                inventorySnapshotStoreService.updateFloatPrice(snapshotId, item.getAssetId(), item.getPrice());
                refined++;
                appendRow(rowsOut, item, Double.valueOf(item.getPrice()));
                continue;
            }
            String key = item.getGoodsId().trim() + '#' + segIndex;
            java.util.List<BuffItem> group = groups.get(key);
            if (group == null) {
                group = new java.util.ArrayList<BuffItem>();
                groups.put(key, group);
                // 查询区间 = [有效档下限, 本段上限]：即“本段或更好段”的最低挂单，就是本段买家能买到的底价。
                groupQueryRange.put(key, new double[] {segments[0][0], segments[segIndex][1]});
            }
            group.add(item);
        }

        // 2) 每组一次挂单查询，组内所有件回填同一价；限流即停（已回填的保留，下次续跑）。
        int queries = 0;
        int groupIndex = 0;
        for (java.util.Map.Entry<String, java.util.List<BuffItem>> entry : groups.entrySet()) {
            java.util.List<BuffItem> group = entry.getValue();
            groupIndex++;
            if (queries >= maxQueries) {
                break;
            }
            BuffItem sample = group.get(0);
            double[] queryRange = groupQueryRange.get(entry.getKey());
            if (progress != null) {
                progress.update(97, Integer.valueOf(groupIndex), Integer.valueOf(groups.size()),
                    "按磨损精估中 " + groupIndex + "/" + groups.size() + " 组：" + sample.getName());
            }
            try {
                queries++;
                Double lowest = buffApiClient.fetchLowestSellOrderPrice(buffProperties.getBaseUrl(), cookie, game, sample.getGoodsId().trim(), queryRange[0], queryRange[1]);
                // 区间内无挂单（此段 float 稀少）时保守回填档价，避免下次重复查询。
                double value = (lowest != null && lowest.doubleValue() > 0.0d) ? lowest.doubleValue() : sample.getPrice();
                for (BuffItem item : group) {
                    inventorySnapshotStoreService.updateFloatPrice(snapshotId, item.getAssetId(), value);
                    refined++;
                    appendRow(rowsOut, item, Double.valueOf(value));
                }
            } catch (BuffRateLimitException ex) {
                rateLimited = true;
                break;
            } catch (Exception ex) {
                log.warn("Refine float price failed, goodsId={}, reason={}", sample.getGoodsId(), ex.getMessage());
                skipped += group.size();
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new int[] {refined, skipped, rateLimited ? 1 : 0, queries};
    }

    private static void appendRow(java.util.List<java.util.Map<String, Object>> rowsOut, BuffItem item, Double value) {
        if (rowsOut == null) {
            return;
        }
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
        row.put("assetId", item.getAssetId());
        row.put("name", item.getName());
        row.put("floatValue", item.getFloatValue());
        row.put("oldPrice", Double.valueOf(item.getPrice()));
        row.put("floatPrice", value);
        rowsOut.add(row);
    }

    // 提交后台精估任务（每账号去重）；拉取线程立即返回。
    private void submitBackgroundRefine(final long accountId, final long snapshotId, final String cookie) {
        final BuffProperties.FloatRefine cfg = buffProperties.getFloatRefine();
        if (!cfg.isAutoRefineOnFetch()) {
            return;
        }
        if (!refineRunningAccounts.add(Long.valueOf(accountId))) {
            log.info("Skip background refine, another run is active, accountId={}", Long.valueOf(accountId));
            return;
        }
        try {
            floatRefineExecutor.submit(new Runnable() {
                public void run() {
                    try {
                        backgroundRefineLoop(accountId, snapshotId, cookie);
                    } finally {
                        refineRunningAccounts.remove(Long.valueOf(accountId));
                    }
                }
            });
        } catch (RuntimeException ex) {
            refineRunningAccounts.remove(Long.valueOf(accountId));
            log.warn("Submit background refine failed, accountId={}, reason={}", Long.valueOf(accountId), ex.getMessage());
        }
    }

    // 后台精估主循环：每轮重查“还没精估价”的件（天然断点续传），被限流就冷却再续，直到清空或达冷却上限。
    private void backgroundRefineLoop(long accountId, long snapshotId, String cookie) {
        BuffProperties.FloatRefine cfg = buffProperties.getFloatRefine();
        int cooldowns = 0;
        while (true) {
            List<BuffItem> pending = new java.util.ArrayList<BuffItem>();
            for (BuffItem item : inventorySnapshotStoreService.loadItems(snapshotId)) {
                if (item.getFloatPrice() == null && item.getPrice() >= cfg.getMinPrice()) {
                    pending.add(item);
                }
            }
            if (pending.isEmpty()) {
                log.info("Background refine finished, accountId={}, snapshotId={}: all items refined", Long.valueOf(accountId), Long.valueOf(snapshotId));
                return;
            }
            // 贵的对 EV 影响大，优先精估。
            pending.sort(new Comparator<BuffItem>() {
                public int compare(BuffItem left, BuffItem right) {
                    return Double.compare(right.getPrice(), left.getPrice());
                }
            });
            int[] result = refineItemsGrouped(snapshotId, cookie, pending, null, null, Math.max(1, cfg.getMaxPerFetch()));
            log.info("Background refine round, accountId={}, snapshotId={}, pending={}, refined={}, skipped={}, queries={}, rateLimited={}, cooldowns={}",
                Long.valueOf(accountId), Long.valueOf(snapshotId), Integer.valueOf(pending.size()), Integer.valueOf(result[0]),
                Integer.valueOf(result[1]), Integer.valueOf(result[3]), Boolean.valueOf(result[2] == 1), Integer.valueOf(cooldowns));
            if (result[2] == 1) {
                cooldowns++;
                if (cooldowns > cfg.getMaxCooldowns()) {
                    log.warn("Background refine giving up after {} cooldowns, accountId={}, snapshotId={} (下次拉取库存会重新触发续跑)",
                        Integer.valueOf(cooldowns - 1), Long.valueOf(accountId), Long.valueOf(snapshotId));
                    return;
                }
                try {
                    Thread.sleep(Math.max(10000L, cfg.getRateLimitCooldownMillis()));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            if (result[0] == 0) {
                // 没被限流也没有任何进展（剩余全是缺字段/异常件），退出防止空转。
                log.info("Background refine stopped with no progress, accountId={}, snapshotId={}, remaining={}",
                    Long.valueOf(accountId), Long.valueOf(snapshotId), Integer.valueOf(pending.size()));
                return;
            }
            // 本轮有进展且未限流：立即进入下一轮（继续消化剩余）。
        }
    }

    @javax.annotation.PreDestroy
    void shutdownFloatRefineExecutor() {
        floatRefineExecutor.shutdownNow();
    }

    public InventoryPageResponse loadPage(InventoryPageRequest request) {
        return loadPage(buffAccountService.resolveDefaultAccountId(), request);
    }

    public InventoryPageResponse loadPage(long accountId, InventoryPageRequest request) {
        buffAccountService.requireAccount(accountId);
        String game = StringUtils.hasText(request.getGame()) ? request.getGame() : buffProperties.getGame();
        InventorySnapshotRecord snapshot = resolveSnapshot(accountId, request.getSnapshotId(), game);
        int page = request.getPage() == null || request.getPage().intValue() < 1 ? 1 : request.getPage().intValue();
        int pageSize = request.getPageSize() == null || request.getPageSize().intValue() < 1 ? 50 : request.getPageSize().intValue();
        String rarity = normalizeRarityFilter(request.getRarity());

        InventorySnapshotSummary summary = inventorySnapshotStoreService.summarizeSnapshot(snapshot.getId(), rarity);
        InventoryPageResponse response = new InventoryPageResponse();
        response.setSnapshotId(Long.valueOf(snapshot.getId()));
        response.setItemCount(summary.getItemCount());
        response.setTradableCount(summary.getTradableCount());
        response.setWithFloatCount(summary.getWithFloatCount());
        response.setTotalCost(summary.getTotalCost());
        response.setTotalItems(inventorySnapshotStoreService.countItems(snapshot.getId(), rarity));
        response.setCurrentPage(page);
        response.setPageSize(pageSize);
        response.setItems(inventorySnapshotStoreService.loadPagedItems(snapshot.getId(), page, pageSize, rarity));
        response.setFetchedAt(formatTimestamp(snapshot.getCreatedAt()));
        log.info("Loaded inventory page, snapshotId={}, page={}, pageSize={}, rarity={}, totalItems={}, returnedItems={}",
            Long.valueOf(snapshot.getId()), Integer.valueOf(page), Integer.valueOf(pageSize), rarity == null ? "all" : rarity,
            Integer.valueOf(response.getTotalItems()), Integer.valueOf(response.getItems().size()));
        return response;
    }

    private String normalizeRarityFilter(String rarity) {
        if (!StringUtils.hasText(rarity) || "all".equals(rarity)) {
            return null;
        }
        return rarity.trim().toLowerCase();
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
            throw new IllegalStateException(ErrorMessages.SHA_256_UNAVAILABLE, ex);
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

    private InventorySnapshotRecord resolveSnapshot(long accountId, Long snapshotId, String game) {
        if (snapshotId != null) {
            Optional<InventorySnapshotRecord> snapshot = inventorySnapshotStoreService.findById(accountId, snapshotId.longValue());
            if (!snapshot.isPresent()) {
                throw new IllegalArgumentException(ErrorMessages.inventorySnapshotNotFound(snapshotId));
            }
            return snapshot.get();
        }
        Optional<InventorySnapshotRecord> latest = inventorySnapshotStoreService.findLatest(accountId, game);
        if (!latest.isPresent()) {
            throw new IllegalArgumentException(ErrorMessages.NO_PERSISTED_INVENTORY_SNAPSHOT);
        }
        return latest.get();
    }
}
