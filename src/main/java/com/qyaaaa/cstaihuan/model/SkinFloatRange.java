package com.qyaaaa.cstaihuan.model;

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
public class SkinFloatRange {
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
    private String image;
    // 规范化基础名，导入时计算，不存在于原始 JSON。
    private String baseNameEn;
    private String baseNameZh;
}
