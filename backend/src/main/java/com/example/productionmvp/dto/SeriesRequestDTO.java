package com.example.productionmvp.dto;

import java.time.LocalDate;
import java.util.UUID;

public class SeriesRequestDTO {
    private UUID modelId;
    private String number;
    private Integer plannedQuantity;
    private String priority;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;

    public UUID getModelId() { return modelId; }
    public void setModelId(UUID modelId) { this.modelId = modelId; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDate plannedStartDate) { this.plannedStartDate = plannedStartDate; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDate plannedEndDate) { this.plannedEndDate = plannedEndDate; }
}
