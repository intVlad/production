package com.example.productionmvp.dto;

import java.util.UUID;

public class BatchRequestDTO {
    private String number;
    private UUID operationId;
    private UUID workerId;
    private UUID sectionId;
    private UUID postId;
    private String materialDetail;
    private Integer quantity;
    private Integer plannedQuantity;
    private Integer actualQuantity;

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }
    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }
    public String getMaterialDetail() { return materialDetail; }
    public void setMaterialDetail(String materialDetail) { this.materialDetail = materialDetail; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public Integer getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(Integer actualQuantity) { this.actualQuantity = actualQuantity; }
}
