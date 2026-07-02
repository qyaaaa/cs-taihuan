package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.BuffSession;
import org.apache.ibatis.annotations.Param;

public interface BuffSessionMapper extends BaseMapper<BuffSession> {
    BuffSession selectByAccountId(@Param("accountId") long accountId);

    int upsertSession(BuffSession session);

    int deleteByAccountId(@Param("accountId") long accountId);
}
