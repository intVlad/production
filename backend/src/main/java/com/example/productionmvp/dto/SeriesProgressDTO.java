package com.example.productionmvp.dto;


public class SeriesProgressDTO {
    private long totalProducts;
    private long completed;
    private double percentage;

    public SeriesProgressDTO() {}

    public SeriesProgressDTO(long totalProducts, long completed, double percentage) {
        this.totalProducts = totalProducts;
        this.completed = completed;
        this.percentage = percentage;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public long getCompleted() {
        return completed;
    }

    public void setCompleted(long completed) {
        this.completed = completed;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }
}
