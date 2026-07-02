package com.qyaaaa.cstaihuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogGroup;
import com.qyaaaa.cstaihuan.model.TradeUpNextTierItem;
import java.util.List;

public interface TradeUpNextTierStoreService extends IService<TradeUpNextTierItem> {
    int replaceForSnapshot(long snapshotId, List<NextTierCatalogGroup> groups);

    int replaceForSnapshot(long accountId, long snapshotId, List<NextTierCatalogGroup> groups);
}
