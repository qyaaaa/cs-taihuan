package com.qyaaaa.cstaihuan.dto;

import java.util.List;

public class NextTierCatalogResponse {
    private Long snapshotId;
    private int sourceItemCount;
    private int groupCount;
    private int targetItemCount;
    private List<NextTierCatalogGroup> groups;

    public NextTierCatalogResponse() {
    }

    public NextTierCatalogResponse(Long snapshotId, int sourceItemCount, int groupCount, int targetItemCount, List<NextTierCatalogGroup> groups) {
        this.snapshotId = snapshotId;
        this.sourceItemCount = sourceItemCount;
        this.groupCount = groupCount;
        this.targetItemCount = targetItemCount;
        this.groups = groups;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public int getSourceItemCount() {
        return sourceItemCount;
    }

    public void setSourceItemCount(int sourceItemCount) {
        this.sourceItemCount = sourceItemCount;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
    }

    public int getTargetItemCount() {
        return targetItemCount;
    }

    public void setTargetItemCount(int targetItemCount) {
        this.targetItemCount = targetItemCount;
    }

    public List<NextTierCatalogGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<NextTierCatalogGroup> groups) {
        this.groups = groups;
    }
}
