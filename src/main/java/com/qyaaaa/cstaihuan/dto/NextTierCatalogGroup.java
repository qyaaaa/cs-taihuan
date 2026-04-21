package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.util.List;
import lombok.Data;

@Data
public class NextTierCatalogGroup {
    private String collection;
    private String baseRarity;
    private String targetRarity;
    private int inventoryCount;
    private List<CatalogSkin> items;
}
