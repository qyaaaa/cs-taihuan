package com.qyaaaa.cstaihuan.model;

public final class Outcome {
    private final CatalogSkin skin;
    private final double probability;
    private final double estimatedFloat;
    private final double estimatedSalePrice;

    public Outcome(CatalogSkin skin, double probability, double estimatedFloat, double estimatedSalePrice) {
        this.skin = skin;
        this.probability = probability;
        this.estimatedFloat = estimatedFloat;
        this.estimatedSalePrice = estimatedSalePrice;
    }

    public CatalogSkin getSkin() {
        return skin;
    }

    public double getProbability() {
        return probability;
    }

    public double getEstimatedFloat() {
        return estimatedFloat;
    }

    public double getEstimatedSalePrice() {
        return estimatedSalePrice;
    }
}

