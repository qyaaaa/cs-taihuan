package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import org.apache.ibatis.annotations.Param;

public interface BuffAccountMapper extends BaseMapper<BuffAccount> {
    BuffAccount selectFirst();

    BuffAccount selectOtherByBuffUserId(@Param("buffUserId") String buffUserId, @Param("excludeAccountId") long excludeAccountId);

    int deleteInventorySnapshotsByAccountId(@Param("accountId") long accountId);

    int deleteTradeUpNextTierItemsByAccountId(@Param("accountId") long accountId);
}
