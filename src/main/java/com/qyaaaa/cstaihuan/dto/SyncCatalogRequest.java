package com.qyaaaa.cstaihuan.dto;

public class SyncCatalogRequest {
    private Long snapshotId;
    private Integer maxDetailRequests;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Integer getMaxDetailRequests() {
        return maxDetailRequests;
    }

    public void setMaxDetailRequests(Integer maxDetailRequests) {
        this.maxDetailRequests = maxDetailRequests;
    }
}
