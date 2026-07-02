package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.InventoryItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotSummary;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InventoryItemMapper extends BaseMapper<InventoryItem> {
    List<InventoryItem> selectWeaponItems(@Param("snapshotId") long snapshotId);

    List<InventoryItem> selectPagedWeaponItems(@Param("snapshotId") long snapshotId, @Param("rarity") String rarity, @Param("limit") int limit, @Param("offset") int offset);

    int countWeaponItems(@Param("snapshotId") long snapshotId, @Param("rarity") String rarity);

    InventorySnapshotSummary summarizeWeaponItems(@Param("snapshotId") long snapshotId, @Param("rarity") String rarity);

    int insertBatch(@Param("items") List<InventoryItem> items);
}
