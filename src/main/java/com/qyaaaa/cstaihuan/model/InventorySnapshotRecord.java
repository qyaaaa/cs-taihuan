package com.qyaaaa.cstaihuan.model;

import lombok.Data;

@Data
public class InventorySnapshotRecord {
    private long id;
    private String game;
    private int itemCount;
    private String fingerprint;
    private long createdAt;
    private long lastSeenAt;
}
