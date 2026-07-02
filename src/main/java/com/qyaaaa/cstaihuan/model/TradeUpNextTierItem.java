package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("trade_up_next_tier_item")
public class TradeUpNextTierItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long snapshotId;
    @TableField("collection_name")
    private String collection;
    private String baseRarity;
    private String targetRarity;
    private Integer inventoryCount;
    private String skinName;
    private BigDecimal skinPrice;
    private Double minFloat;
    private Double maxFloat;
    private Long createdAt;
}
