package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("catalog_sync_task")
public class CatalogSyncTaskRecord {
    @TableId(type = IdType.AUTO)
    private long id;
    private long accountId;
    private long snapshotId;
    private String goodsId;
    @TableField("collection_name")
    private String collection;
    private String status;
    private String failureReason;
    private int retryCount;
    private Long lastAttemptAt;
    private long createdAt;
    private long updatedAt;
}
