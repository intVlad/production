package com.example.productionmvp.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.example.productionmvp.model.Batch;

public class BatchDTO {
    private UUID id;
    private String number;
    private UUID operationId;
    private String operationName;
    private UUID workerId;
    private String workerName;
    private UUID sectionId;
    private String sectionName;
    private UUID postId;
    private String postName;
    private String materialDetail;
    private int plannedQuantity;
    private int actualQuantity;
    private int distributedQuantity;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public BatchDTO() {}

    public BatchDTO(Batch batch) {
        this.id = batch.getId();
        this.number = batch.getNumber();
        if (batch.getOperation() != null) {
            this.operationId = batch.getOperation().getId();
            this.operationName = batch.getOperation().getName();
        }
        if (batch.getWorker() != null) {
            this.workerId = batch.getWorker().getId();
            this.workerName = batch.getWorker().getName();
        }
        if (batch.getSection() != null) {
            this.sectionId = batch.getSection().getId();
            this.sectionName = batch.getSection().getName();
        }
        if (batch.getPost() != null) {
            this.postId = batch.getPost().getId();
            this.postName = batch.getPost().getName();
        }
        this.materialDetail = batch.getMaterialDetail();
        this.plannedQuantity = batch.getPlannedQuantity();
        this.actualQuantity = batch.getActualQuantity();
        this.distributedQuantity = batch.getDistributedQuantity() != null ? batch.getDistributedQuantity() : 0;
        if (batch.getStatus() != null) this.status = batch.getStatus().name();
        this.startTime = batch.getStartedAt();
        this.endTime = batch.getCompletedAt();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }
    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }
    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public String getMaterialDetail() { return materialDetail; }
    public void setMaterialDetail(String materialDetail) { this.materialDetail = materialDetail; }
    public int getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(int plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public int getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(int actualQuantity) { this.actualQuantity = actualQuantity; }
    public int getDistributedQuantity() { return distributedQuantity; }
    public void setDistributedQuantity(int distributedQuantity) { this.distributedQuantity = distributedQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
