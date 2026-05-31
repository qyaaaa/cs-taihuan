package com.qyaaaa.cstaihuan.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One skin's authoritative wear (float) range, sourced from a bundled full-catalog snapshot.
 * Also used to deserialize the snapshot JSON, so JSON field names map directly.
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
    // Normalized base names (computed at import time, not present in the JSON).
    private String baseNameEn;
    private String baseNameZh;
}
