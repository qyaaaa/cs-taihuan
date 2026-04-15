package com.qyaaaa.cstaihuan.model;

public class Outcome {
    private CatalogSkin skin;
    private double probability;
    private double estimatedFloat;
    private double estimatedSalePrice;

    public Outcome() {
    }

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

    public void setSkin(CatalogSkin skin) {
        this.skin = skin;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public void setEstimatedFloat(double estimatedFloat) {
        this.estimatedFloat = estimatedFloat;
    }

    public void setEstimatedSalePrice(double estimatedSalePrice) {
        this.estimatedSalePrice = estimatedSalePrice;
    }
}
