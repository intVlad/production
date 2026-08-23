package com.example.productionmvp.dto;

import com.example.productionmvp.model.DefectResolution;

import java.util.UUID;

public class TaskActionRequestDTO {
    private UUID workerId;
    private String reason;
    private DefectResolution resolution;

    public UUID getWorkerId() {
        return workerId;
    }

    public void setWorkerId(UUID workerId) {
        this.workerId = workerId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public DefectResolution getResolution() {
        return resolution;
    }

    public void setResolution(DefectResolution resolution) {
        this.resolution = resolution;
    }
}
