package com.qyaaaa.cstaihuan.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NextTierCatalogResponse {
    private Long snapshotId;
    private int sourceItemCount;
    private int groupCount;
    private int targetItemCount;
    private List<NextTierCatalogGroup> groups;
}
