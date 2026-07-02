package com.qyaaaa.cstaihuan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个皮肤的权威磨损范围，来源于内置全量目录快照。
 * 同时用于反序列化快照 JSON，因此字段名需要与 JSON 直接对应。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@TableName("skin_float_range")
public class SkinFloatRange {
    @JsonIgnore
    @TableId(value = "id", type = IdType.AUTO)
    private Long rowId;
    @JsonProperty("id")
    private String skinId;
    private String paintIndex;
    private String nameEn;
    private String nameZh;
    private String weapon;
    private String rarity;
    private double minFloat;
    private double maxFloat;
    private String collectionEn;
    private String collectionZh;
    // 皮肤图标 URL，来源于快照的 image 字段（按 id 从 ByMykel/CSGO-API 合并）。
    @TableField("image_url")
    private String image;
    @JsonIgnore
    private String source;
    @JsonIgnore
    private Long updatedAt;
    // 规范化基础名，导入时计算，不存在于原始 JSON。
    private String baseNameEn;
    private String baseNameZh;
}
