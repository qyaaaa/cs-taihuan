package com.qyaaaa.cstaihuan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qyaaaa.cstaihuan.dto.NextTierCatalogGroup;
import com.qyaaaa.cstaihuan.mapper.TradeUpNextTierItemMapper;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.TradeUpNextTierItem;
import com.qyaaaa.cstaihuan.service.TradeUpNextTierStoreService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeUpNextTierStoreServiceImpl extends ServiceImpl<TradeUpNextTierItemMapper, TradeUpNextTierItem> implements TradeUpNextTierStoreService {
    @Override
    @Transactional
    public int replaceForSnapshot(long snapshotId, List<NextTierCatalogGroup> groups) {
        return replaceForSnapshot(1L, snapshotId, groups);
    }

    /**
     * 下一档推荐是快照级缓存，生成前先清空当前账号当前快照的旧结果，再批量写入新结果。
     */
    @Override
    @Transactional
    public int replaceForSnapshot(long accountId, long snapshotId, List<NextTierCatalogGroup> groups) {
        remove(new LambdaQueryWrapper<TradeUpNextTierItem>()
            .eq(TradeUpNextTierItem::getAccountId, accountId)
            .eq(TradeUpNextTierItem::getSnapshotId, snapshotId));
        if (groups == null || groups.isEmpty()) {
            return 0;
        }

        long now = System.currentTimeMillis();
        List<TradeUpNextTierItem> rows = new ArrayList<TradeUpNextTierItem>();
        for (NextTierCatalogGroup group : groups) {
            if (group.getItems() == null || group.getItems().isEmpty()) {
                continue;
            }
            for (CatalogSkin skin : group.getItems()) {
                rows.add(toEntity(accountId, snapshotId, now, group, skin));
            }
        }
        if (rows.isEmpty()) {
            return 0;
        }
        baseMapper.insertBatch(rows);
        return rows.size();
    }

    private TradeUpNextTierItem toEntity(long accountId, long snapshotId, long createdAt, NextTierCatalogGroup group, CatalogSkin skin) {
        TradeUpNextTierItem item = new TradeUpNextTierItem();
        item.setAccountId(Long.valueOf(accountId));
        item.setSnapshotId(Long.valueOf(snapshotId));
        item.setCollection(group.getCollection());
        item.setBaseRarity(group.getBaseRarity());
        item.setTargetRarity(group.getTargetRarity());
        item.setInventoryCount(Integer.valueOf(group.getInventoryCount()));
        item.setSkinName(skin.getName());
        item.setSkinPrice(BigDecimal.valueOf(skin.getPrice()));
        item.setMinFloat(Double.valueOf(skin.getMinFloat()));
        item.setMaxFloat(Double.valueOf(skin.getMaxFloat()));
        item.setCreatedAt(Long.valueOf(createdAt));
        return item;
    }
}
