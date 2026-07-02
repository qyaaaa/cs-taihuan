package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("buff_account")
public class BuffAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;
    private String buffUserId;
    private String maskedCookie;
    private String status;
    private String lastValidatedAt;
    private Long createdAt;
    private Long updatedAt;
}
