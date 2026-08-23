package com.example.productionmvp.dto;

import java.util.UUID;

public class DefectRequestDTO {
    private UUID assemblyInstanceId;
    private UUID taskId;
    private String reason;
    private UUID confirmedById;
    private com.example.productionmvp.model.DefectResolution resolution;

    public UUID getAssemblyInstanceId() { return assemblyInstanceId; }
    public void setAssemblyInstanceId(UUID assemblyInstanceId) { this.assemblyInstanceId = assemblyInstanceId; }

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getConfirmedById() { return confirmedById; }
    public void setConfirmedById(UUID confirmedById) { this.confirmedById = confirmedById; }
    
    public com.example.productionmvp.model.DefectResolution getResolution() { return resolution; }
    public void setResolution(com.example.productionmvp.model.DefectResolution resolution) { this.resolution = resolution; }
}
