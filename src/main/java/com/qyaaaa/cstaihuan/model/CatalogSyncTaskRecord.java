package com.qyaaaa.cstaihuan.model;

import lombok.Data;

@Data
public class CatalogSyncTaskRecord {
    private long id;
    private long accountId;
    private long snapshotId;
    private String goodsId;
    private String collection;
    private String status;
    private String failureReason;
    private int retryCount;
    private Long lastAttemptAt;
    private long createdAt;
    private long updatedAt;
}
