package com.example.productionmvp.dto;

import com.example.productionmvp.model.HistoryEvent;

import java.time.LocalDateTime;
import java.util.UUID;

// Checklist §39/§65-68: a dedicated audit screen needs to answer "хто, коли, що зробив" for
// every action, not just the last 10 events embedded in the dashboard payload. Flat DTO (not
// the raw HistoryEvent entity) for the same reason DashboardService builds recentHistory as a
// Map rather than returning entities directly - avoids relying on every relation on this
// entity being circular-reference-safe to serialize.
public class HistoryEventDTO {
    private UUID id;
    private String action;
    private LocalDateTime timestamp;
    private UUID workerId;
    private String workerName;
    private UUID taskId;
    private String productSerial;
    private String operationName;
    private String seriesNumber;
    private String batchNumber;

    public HistoryEventDTO(HistoryEvent event) {
        this.id = event.getId();
        this.action = event.getAction();
        this.timestamp = event.getTimestamp();
        if (event.getWorker() != null) {
            this.workerId = event.getWorker().getId();
            this.workerName = event.getWorker().getName();
        }
        if (event.getTask() != null) {
            this.taskId = event.getTask().getId();
        }
        this.productSerial = event.getProductInstance() != null ? event.getProductInstance().getSerialNumber() : null;
        this.operationName = event.getOperation() != null ? event.getOperation().getName()
                : (event.getStage() != null ? event.getStage().getName() : null);
        this.seriesNumber = event.getSeries() != null ? event.getSeries().getNumber() : null;
        this.batchNumber = event.getBatch() != null ? event.getBatch().getNumber() : null;
    }

    public UUID getId() { return id; }
    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public UUID getWorkerId() { return workerId; }
    public String getWorkerName() { return workerName; }
    public UUID getTaskId() { return taskId; }
    public String getProductSerial() { return productSerial; }
    public String getOperationName() { return operationName; }
    public String getSeriesNumber() { return seriesNumber; }
    public String getBatchNumber() { return batchNumber; }
}
