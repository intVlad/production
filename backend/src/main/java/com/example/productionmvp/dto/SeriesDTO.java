package com.example.productionmvp.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.example.productionmvp.model.Series;

public class SeriesDTO {
    private UUID id;
    private ProductModelDTO productModel;
    private String number;
    private int plannedQuantity;
    private String priority;
    private LocalDateTime plannedStartDate;
    private LocalDateTime plannedEndDate;
    private String status;

    public SeriesDTO() {}

    public SeriesDTO(Series series) {
        this.id = series.getId();
        if (series.getProductModel() != null) {
            this.productModel = new ProductModelDTO(series.getProductModel());
        }
        this.number = series.getNumber();
        if (series.getPlannedQuantity() != null) {
            this.plannedQuantity = series.getPlannedQuantity();
        }
        if (series.getPriority() != null) {
            this.priority = series.getPriority().name();
        }
        this.plannedStartDate = series.getPlannedStartDate();
        this.plannedEndDate = series.getPlannedEndDate();
        if (series.getStatus() != null) {
            this.status = series.getStatus().name();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductModelDTO getProductModel() { return productModel; }
    public void setProductModel(ProductModelDTO productModel) { this.productModel = productModel; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public int getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(int plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public LocalDateTime getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDateTime plannedStartDate) { this.plannedStartDate = plannedStartDate; }
    public LocalDateTime getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDateTime plannedEndDate) { this.plannedEndDate = plannedEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
