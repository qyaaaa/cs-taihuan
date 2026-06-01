package com.qyaaaa.cstaihuan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.dto.SyncCatalogRequest;
import com.qyaaaa.cstaihuan.dto.SyncCatalogResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.service.AsyncTaskService;
import com.qyaaaa.cstaihuan.service.BuffAccountService;
import com.qyaaaa.cstaihuan.service.BuffApiClient;
import com.qyaaaa.cstaihuan.service.BuffSessionService;
import com.qyaaaa.cstaihuan.service.CatalogApplicationService;
import com.qyaaaa.cstaihuan.service.CatalogService;
import com.qyaaaa.cstaihuan.service.CatalogSyncTaskStoreService;
import com.qyaaaa.cstaihuan.service.InventorySnapshotStoreService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 CatalogApplicationService 的核心逻辑：
 *  1. 正常全量同步
 *  2. 限流后局部保存 + 失败任务 requeue（partial=true）
 *  3. 首个请求就限流且无已处理数据时重新抛出异常
 *  4. 全部 goods 在缓存内时不发任何 API 请求
 *
 * requestIntervalMillis 在测试中设为 1ms，使测试飞速执行，
 * 但覆盖逻辑路径与生产完全一致，无论配置 5000ms 还是 2000ms 代码行为相同。
 */
@ExtendWith(MockitoExtension.class)
class CatalogApplicationServiceTest {

    @Mock private BuffApiClient buffApiClient;
    @Mock private CatalogService catalogService;
    @Mock private CatalogSyncTaskStoreService taskStore;
    @Mock private InventorySnapshotStoreService snapshotStore;
    @Mock private BuffSessionService buffSessionService;
    @Mock private BuffAccountService buffAccountService;

    private BuffProperties buffProperties;
    private CatalogApplicationService service;

    private static final long ACCOUNT_ID = 1L;
    private static final long SNAPSHOT_ID = 42L;

    @BeforeEach
    void setUp() {
        buffProperties = new BuffProperties();
        BuffProperties.CatalogSync cs = buffProperties.getCatalogSync();
        // 测试中不真正等待，仅验证逻辑分支
        cs.setRequestIntervalMillis(1L);
        cs.setCacheFreshMillis(3_600_000L);
        cs.setMaxDetailRequestsPerRun(0); // 0 = 不限量

        service = new CatalogApplicationService(
            catalogService, snapshotStore, buffSessionService,
            buffApiClient, buffProperties, taskStore, buffAccountService
        );
    }

    // ── 公共 mock 准备 ───────────────────────────────────────────────────────

    private void commonAccountAndSnapshotMocks(List<BuffItem> inventory) throws Exception {
        commonAccountAndSnapshotMocks(inventory, new LinkedHashSet<String>());
    }

    private void commonAccountAndSnapshotMocks(List<BuffItem> inventory, Set<String> freshGoodsIds) throws Exception {
        when(buffAccountService.resolveDefaultAccountId()).thenReturn(ACCOUNT_ID);
        when(snapshotStore.findLatest(eq(ACCOUNT_ID), any())).thenReturn(Optional.of(snapshot(SNAPSHOT_ID)));
        when(snapshotStore.loadItems(SNAPSHOT_ID)).thenReturn(inventory);
        when(buffSessionService.resolveCookie(eq(ACCOUNT_ID), any())).thenReturn("session=test");
        when(catalogService.loadFreshGoodsIds(anyLong())).thenReturn(freshGoodsIds);
    }

    // ── 构造辅助 ─────────────────────────────────────────────────────────────

    private static InventorySnapshotRecord snapshot(long id) {
        InventorySnapshotRecord r = new InventorySnapshotRecord();
        r.setId(id);
        return r;
    }

    private static BuffItem item(String name, String goodsId, String collection) {
        return new BuffItem(
            goodsId, name, 100.0, null, null, null, null,
            collection, "mil-spec", "weapon_ak47", "mil-spec", "军规级",
            true, goodsId, new LinkedHashMap<String, Object>()
        );
    }

    private static CatalogSyncTaskRecord task(long id, String goodsId, String collection) {
        CatalogSyncTaskRecord t = new CatalogSyncTaskRecord();
        t.setId(id);
        t.setGoodsId(goodsId);
        t.setCollection(collection);
        t.setStatus(CatalogSyncTaskStoreService.STATUS_PROCESSING);
        t.setRetryCount(1);
        return t;
    }

    private static CatalogSkin skin(String goodsId, String name, String collection) {
        CatalogSkin s = new CatalogSkin();
        s.setGoodsId(goodsId);
        s.setName(name);
        s.setCollection(collection);
        s.setRarity("mil-spec");
        s.setCategoryKey("weapon_ak47");
        s.setQualityLabel("军规级");
        s.setPrice(100.0);
        return s;
    }

    private static Map<String, Object> emptyPayload() {
        return new LinkedHashMap<String, Object>();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 测试 1：所有 goods 正常拉取，全量同步成功
    // ════════════════════════════════════════════════════════════════════════
    @Test
    void allGoodsSucceed_returnsNonPartialWithCorrectCounts() throws Exception {
        List<BuffItem> inventory = Arrays.asList(
            item("AK-47 | Redline", "101", "Overpass Collection"),
            item("M4A4 | Asiimov",  "102", "Phoenix Collection")
        );
        commonAccountAndSnapshotMocks(inventory);

        // 任务队列：返回两条任务，第三次返回空（队列耗尽）
        when(taskStore.claimNext(SNAPSHOT_ID))
            .thenReturn(Optional.of(task(1, "101", "Overpass Collection")))
            .thenReturn(Optional.of(task(2, "102", "Phoenix Collection")))
            .thenReturn(Optional.empty());
        when(taskStore.countOpen(SNAPSHOT_ID)).thenReturn(1).thenReturn(0);
        when(taskStore.countAll(SNAPSHOT_ID)).thenReturn(2);

        when(buffApiClient.fetchGoodsDetail(any(), any(), any(), any())).thenReturn(emptyPayload());
        when(buffApiClient.parseCatalogSkinFromGoodsDetail(any(), anyString()))
            .thenReturn(skin("101", "AK-47 | Redline", "Overpass Collection"))
            .thenReturn(skin("102", "M4A4 | Asiimov",  "Phoenix Collection"));
        when(buffApiClient.extractRelatedCatalogSkins(any(), any())).thenReturn(Collections.<CatalogSkin>emptyList());
        when(buffApiClient.extractRelatedGoodsIds(any())).thenReturn(Collections.<String>emptyList());
        when(catalogService.upsertAll(any())).thenReturn(2);

        SyncCatalogResponse resp = service.syncCatalog(new SyncCatalogRequest());

        assertThat(resp.isPartial()).as("全量同步不应返回 partial").isFalse();
        assertThat(resp.getProcessedGoodsCount()).isEqualTo(2);
        assertThat(resp.getRemainingGoodsCount()).isEqualTo(0);
        // 两次 goods 详情请求
        verify(buffApiClient, times(2)).fetchGoodsDetail(any(), any(), any(), any());
        // 至少一次 upsertAll 写库
        verify(catalogService, atLeastOnce()).upsertAll(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 测试 2：第 2 个 goods 触发限流，系统局部保存已获取数据并标记 partial
    // ════════════════════════════════════════════════════════════════════════
    @Test
    void rateLimitAfterFirstGood_savesPartialAndRequeuesToPending() throws Exception {
        List<BuffItem> inventory = Arrays.asList(
            item("AK-47 | Redline", "101", "Overpass Collection"),
            item("M4A4 | Asiimov",  "102", "Phoenix Collection")
        );
        commonAccountAndSnapshotMocks(inventory);

        when(taskStore.claimNext(SNAPSHOT_ID))
            .thenReturn(Optional.of(task(1, "101", "Overpass Collection")))  // 第 1 个：成功
            .thenReturn(Optional.of(task(2, "102", "Phoenix Collection")));   // 第 2 个：限流
        when(taskStore.countOpen(SNAPSHOT_ID)).thenReturn(1);
        when(taskStore.countAll(SNAPSHOT_ID)).thenReturn(2);

        // goods 101 成功，goods 102 触发 HTTP 429
        when(buffApiClient.fetchGoodsDetail(any(), any(), any(), eq("101"))).thenReturn(emptyPayload());
        when(buffApiClient.fetchGoodsDetail(any(), any(), any(), eq("102")))
            .thenThrow(new BuffRateLimitException("BUFF 当前触发限流"));
        when(buffApiClient.parseCatalogSkinFromGoodsDetail(any(), any()))
            .thenReturn(skin("101", "AK-47 | Redline", "Overpass Collection"));
        when(buffApiClient.extractRelatedCatalogSkins(any(), any())).thenReturn(Collections.<CatalogSkin>emptyList());
        when(buffApiClient.extractRelatedGoodsIds(any())).thenReturn(Collections.<String>emptyList());
        when(catalogService.upsertAll(any())).thenReturn(1);

        SyncCatalogResponse resp = service.syncCatalog(new SyncCatalogRequest());

        // 关键断言：返回 partial，已拿到 1 个 goods 的数据
        assertThat(resp.isPartial()).as("限流后应返回 partial=true").isTrue();
        assertThat(resp.getProcessedGoodsCount()).as("只成功处理了 1 个").isEqualTo(1);

        // task 2（goods 102）被放回 PENDING 队列，下次定时任务继续
        verify(taskStore).requeue(eq(2L), anyString());

        // goods 101 的数据已写入数据库，不会丢失
        verify(catalogService, atLeastOnce()).upsertAll(
            argThat((List<CatalogSkin> list) -> !list.isEmpty())
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // 测试 3：第一个 goods 就限流且没有任何已处理数据，应重新抛出异常
    //         （业务上相当于本轮什么也没做，下次定时任务会重试）
    // ════════════════════════════════════════════════════════════════════════
    @Test
    void rateLimitOnVeryFirstGood_rethrowsWhenNoDataCollected() throws Exception {
        List<BuffItem> inventory = Collections.singletonList(
            item("AK-47 | Redline", "101", "Overpass Collection")
        );
        commonAccountAndSnapshotMocks(inventory);

        when(taskStore.claimNext(SNAPSHOT_ID))
            .thenReturn(Optional.of(task(1, "101", "Overpass Collection")));

        when(buffApiClient.fetchGoodsDetail(any(), any(), any(), any()))
            .thenThrow(new BuffRateLimitException("BUFF 当前触发限流"));

        // 抛出限流异常，不是静默吞掉
        assertThrows(BuffRateLimitException.class,
            () -> service.syncCatalog(new SyncCatalogRequest()),
            "首个 goods 就限流且无已有数据时，应向上抛出 BuffRateLimitException"
        );
        // 失败的任务仍被放回队列
        verify(taskStore).requeue(eq(1L), anyString());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 测试 4：所有 goods 均已在 1 小时缓存内，不应发出任何 BUFF API 请求
    // ════════════════════════════════════════════════════════════════════════
    @Test
    void allGoodsAlreadyFresh_noApiCallsMade() throws Exception {
        List<BuffItem> inventory = Arrays.asList(
            item("AK-47 | Redline", "101", "Overpass Collection"),
            item("M4A4 | Asiimov",  "102", "Phoenix Collection")
        );
        // 两个 goods 都在新鲜缓存内
        Set<String> fresh = new LinkedHashSet<String>(Arrays.asList("101", "102"));
        commonAccountAndSnapshotMocks(inventory, fresh);

        // 队列为空（seed 全被过滤，没有需要拉取的任务）
        when(taskStore.claimNext(SNAPSHOT_ID)).thenReturn(Optional.<CatalogSyncTaskRecord>empty());
        when(taskStore.countOpen(SNAPSHOT_ID)).thenReturn(0);
        when(taskStore.countAll(SNAPSHOT_ID)).thenReturn(0);
        when(catalogService.upsertAll(any())).thenReturn(0);

        SyncCatalogResponse resp = service.syncCatalog(new SyncCatalogRequest());

        // 完全不打 BUFF 接口
        verify(buffApiClient, never()).fetchGoodsDetail(any(), any(), any(), any());
        assertThat(resp.getSkippedExistingCount())
            .as("两个 goods 都应作为 fresh 跳过").isEqualTo(2);
        assertThat(resp.getProcessedGoodsCount()).isZero();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 测试 5：带进度回调时，限流后 partial 行为与无回调一致
    // ════════════════════════════════════════════════════════════════════════
    @Test
    void rateLimitWithProgressCallback_stillReturnsPartial() throws Exception {
        List<BuffItem> inventory = Arrays.asList(
            item("AK-47 | Redline", "101", "Overpass Collection"),
            item("M4A4 | Asiimov",  "102", "Phoenix Collection")
        );
        commonAccountAndSnapshotMocks(inventory);

        when(taskStore.claimNext(SNAPSHOT_ID))
            .thenReturn(Optional.of(task(1, "101", "Overpass Collection")))
            .thenReturn(Optional.of(task(2, "102", "Phoenix Collection")));
        when(taskStore.countOpen(SNAPSHOT_ID)).thenReturn(1);
        when(taskStore.countAll(SNAPSHOT_ID)).thenReturn(2);

        when(buffApiClient.fetchGoodsDetail(any(), any(), any(), eq("101"))).thenReturn(emptyPayload());
        when(buffApiClient.fetchGoodsDetail(any(), any(), any(), eq("102")))
            .thenThrow(new BuffRateLimitException("BUFF 当前触发限流"));
        when(buffApiClient.parseCatalogSkinFromGoodsDetail(any(), any()))
            .thenReturn(skin("101", "AK-47 | Redline", "Overpass Collection"));
        when(buffApiClient.extractRelatedCatalogSkins(any(), any())).thenReturn(Collections.<CatalogSkin>emptyList());
        when(buffApiClient.extractRelatedGoodsIds(any())).thenReturn(Collections.<String>emptyList());
        when(catalogService.upsertAll(any())).thenReturn(1);

        // 用 no-op progress 模拟异步任务场景
        AsyncTaskService.TaskProgress progress = new AsyncTaskService.TaskProgress() {
            public void update(int pct, Integer cur, Integer total, String msg) {}
            public void message(String msg) {}
        };

        SyncCatalogResponse resp = service.syncCatalogAsync(new SyncCatalogRequest(), progress);

        assertThat(resp.isPartial()).isTrue();
        assertThat(resp.getProcessedGoodsCount()).isEqualTo(1);
        verify(taskStore).requeue(eq(2L), anyString());
    }
}
