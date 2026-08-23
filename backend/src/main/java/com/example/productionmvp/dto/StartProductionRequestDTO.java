package com.example.productionmvp.dto;

import java.util.UUID;

public class StartProductionRequestDTO {
    private UUID modelId;
    private String serialNumber;
    private UUID workerId;

    public StartProductionRequestDTO() {}

    public UUID getModelId() {
        return modelId;
    }

    public void setModelId(UUID modelId) {
        this.modelId = modelId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public void setWorkerId(UUID workerId) {
        this.workerId = workerId;
    }
}
