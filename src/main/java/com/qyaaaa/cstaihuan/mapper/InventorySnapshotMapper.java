package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import org.apache.ibatis.annotations.Param;

public interface InventorySnapshotMapper extends BaseMapper<InventorySnapshotRecord> {
    InventorySnapshotRecord selectLatest(@Param("accountId") long accountId, @Param("game") String game);

    InventorySnapshotRecord selectByAccountAndId(@Param("accountId") long accountId, @Param("snapshotId") long snapshotId);
}
