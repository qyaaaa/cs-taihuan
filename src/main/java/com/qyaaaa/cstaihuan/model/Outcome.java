package com.qyaaaa.cstaihuan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Outcome {
    private CatalogSkin skin;
    private double probability;
    private double estimatedFloat;
    private double estimatedSalePrice;
}
