package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.TradeUpNextTierItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TradeUpNextTierItemMapper extends BaseMapper<TradeUpNextTierItem> {
    int insertBatch(@Param("items") List<TradeUpNextTierItem> items);
}
