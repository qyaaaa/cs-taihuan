package com.qyaaaa.cstaihuan.dto;

public class FetchInventoryResponse {
    private int itemCount;
    private String outputPath;

    public FetchInventoryResponse() {
    }

    public FetchInventoryResponse(int itemCount, String outputPath) {
        this.itemCount = itemCount;
        this.outputPath = outputPath;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
}

