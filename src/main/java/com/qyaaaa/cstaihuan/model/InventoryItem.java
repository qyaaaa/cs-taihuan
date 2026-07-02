package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("inventory_item")
public class InventoryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long snapshotId;
    private String assetId;
    private String goodsId;
    private String name;
    private BigDecimal price;
    private Double floatValue;
    private String floatValueRaw;
    private String imageUrl;
    private String wearName;
    @TableField("collection_name")
    private String collection;
    private String rarity;
    private String categoryKey;
    private String filterRarity;
    private String qualityLabel;
    private Boolean tradable;
    private String rawJson;
}
