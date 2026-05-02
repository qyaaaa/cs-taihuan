package com.qyaaaa.cstaihuan.exception;

public final class ErrorMessages {
    public static final String VALIDATION_FAILED = "请求参数校验失败。";
    public static final String INTERNAL_API_ERROR = "服务器内部错误，请查看后端日志。";
    public static final String INTERNAL_TASK_ERROR = "任务执行失败，请查看后端日志。";

    public static final String OUTPUT_PATH_REQUIRED = "outputPath is required.";
    public static final String INVENTORY_PATH_REQUIRED = "inventoryPath is required.";
    public static final String INVENTORY_SNAPSHOT_NOT_FOUND_PREFIX = "Inventory snapshot was not found: ";
    public static final String NO_PERSISTED_INVENTORY_SNAPSHOT = "No persisted inventory snapshot was found.";
    public static final String ASYNC_TASK_NOT_FOUND_PREFIX = "Async task was not found: ";
    public static final String CATALOG_EMPTY = "Catalog 数据库为空，请先同步 Catalog 数据。";
    public static final String CATALOG_SYNC_ALREADY_RUNNING = "已有目录同步任务正在运行，请等待当前任务完成后再试。";
    public static final String CATALOG_SYNC_EMPTY_INVENTORY = "当前库存快照为空，无法生成 Catalog。";
    public static final String CATALOG_SYNC_MISSING_GOODS_ID = "当前库存快照中缺少有效的 goods_id，无法同步 Catalog。";
    public static final String BUFF_SESSION_INVALID = "BUFF 会话已失效或 Cookie 不完整，请重新登录 BUFF 后导入 Cookie。";
    public static final String COOKIE_REQUIRED = "cookie is required.";
    public static final String BUFF_COOKIE_MISSING = "BUFF cookie is missing. Please import a session from the frontend first.";
    public static final String NO_BUFF_SESSION_SAVED = "No BUFF session has been saved.";
    public static final String BUFF_RATE_LIMIT = "BUFF 当前触发限流，请稍后再试。若数据库里已有库存快照，系统会优先回退到最近一次保存的数据。";
    public static final String BUFF_CONNECTION_RESET = "BUFF 当前连接被远端重置，通常是请求过快或风控导致。本次已尽量保留已抓到的数据，请稍后继续。";
    public static final String BUFF_API_REQUEST_FAILED_PREFIX = "BUFF API request failed: ";
    public static final String SHA_256_UNAVAILABLE = "SHA-256 is unavailable.";
    public static final String BUFF_INVENTORY_PAGE_WAIT_INTERRUPTED = "Interrupted while waiting to request the next BUFF inventory page.";
    public static final String CATALOG_SYNC_INTERRUPTED = "Catalog sync was interrupted.";
    public static final String CREATE_INVENTORY_SNAPSHOT_FAILED = "Failed to create inventory snapshot.";
    public static final String READ_RAW_INVENTORY_ITEM_FAILED = "Failed to read persisted raw inventory item.";
    public static final String PERSIST_RAW_INVENTORY_ITEM_FAILED = "Failed to persist raw inventory item.";
    public static final String BUFF_SESSION_VALIDATE_FAILED = "会话校验失败，请重新登录 BUFF 后导入最新 Cookie。";
    public static final String BUFF_ACCOUNT_KEEP_ONE = "至少需要保留一个账号。";
    public static final String BUFF_ACCOUNT_NICKNAME_SIZE = "nickname 长度不能超过 64";
    public static final String BUFF_ACCOUNT_USER_ID_SIZE = "buffUserId 长度不能超过 128";

    public static final String SNAPSHOT_ID_POSITIVE = "snapshotId 必须大于 0";
    public static final String OUTPUT_PATH_NOT_BLANK = "outputPath 不能为空";
    public static final String INVENTORY_PATH_NOT_BLANK = "inventoryPath 不能为空";
    public static final String COOKIE_NOT_BLANK = "cookie 不能为空";
    public static final String GAME_SIZE = "game 长度不能超过 32";
    public static final String SOURCE_SIZE = "source 长度不能超过 64";
    public static final String PAGE_MIN = "page 不能小于 1";
    public static final String PAGE_SIZE_MIN = "pageSize 不能小于 1";
    public static final String PAGE_SIZE_MAX = "pageSize 不能大于 200";
    public static final String MAX_PAGES_MIN = "maxPages 不能小于 1";
    public static final String MAX_PAGES_MAX = "maxPages 不能大于 1000";
    public static final String MAX_DETAIL_REQUESTS_MIN = "maxDetailRequests 不能小于 1";
    public static final String MAX_DETAIL_REQUESTS_MAX = "maxDetailRequests 不能大于 200";
    public static final String TOP_K_MIN = "topK 不能小于 1";
    public static final String TOP_K_MAX = "topK 不能大于 50";
    public static final String SALE_FEE_RATE_MIN = "saleFeeRate 不能小于 0";
    public static final String SALE_FEE_RATE_MAX = "saleFeeRate 不能大于 0.99";
    public static final String MAX_ITEMS_PER_RARITY_MIN = "maxItemsPerRarity 不能小于 1";
    public static final String MAX_ITEMS_PER_RARITY_MAX = "maxItemsPerRarity 不能大于 1000";
    public static final String MAX_COMBINATIONS_MIN = "maxCombinations 不能小于 1";
    public static final String MAX_COMBINATIONS_MAX = "maxCombinations 不能大于 1000000";
    public static final String SORT_BY_UNSUPPORTED = "sortBy 不支持";
    public static final String RARITY_UNSUPPORTED = "rarity 不支持";
    public static final String TRACK_TYPE_UNSUPPORTED = "trackType 不支持";
    public static final String CONTRACT_TYPE_UNSUPPORTED = "contractType 不支持";

    private ErrorMessages() {
    }

    public static String inventorySnapshotNotFound(Long snapshotId) {
        return INVENTORY_SNAPSHOT_NOT_FOUND_PREFIX + snapshotId;
    }

    public static String asyncTaskNotFound(String taskId) {
        return ASYNC_TASK_NOT_FOUND_PREFIX + taskId;
    }

    public static String catalogSyncIncomplete(int remainingCount) {
        return "目录同步尚未完成，当前快照还有 " + remainingCount + " 个 goods 待处理。请继续点击“从 BUFF 同步目录数据”，直到剩余为 0 后再生成方案。";
    }

    public static String buffApiRequestFailed(Object status) {
        return BUFF_API_REQUEST_FAILED_PREFIX + status;
    }

    public static String buffAccountNotFound(long accountId) {
        return "BUFF 账号不存在：" + accountId;
    }
}
