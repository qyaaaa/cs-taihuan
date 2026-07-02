package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("buff_session")
public class BuffSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String cookieText;
    private String source;
    private String createdAt;
    private String updatedAt;
    private String lastValidatedAt;
}
