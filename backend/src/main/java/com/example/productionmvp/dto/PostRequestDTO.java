package com.example.productionmvp.dto;

import java.util.UUID;

public class PostRequestDTO {
    private String name;
    private Integer maxCapacity;
    private UUID sectionId;
    // Comma-separated operation names this post is equipped for. Both task creation paths
    // (TaskExecutionService.createIndividualTask and BatchService.createBatch) refuse to put
    // an operation on a post whose list doesn't contain it - but nothing could ever fill this
    // field, so the check read as implemented while being unreachable on every real post.
    private String operationTypes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOperationTypes() { return operationTypes; }
    public void setOperationTypes(String operationTypes) { this.operationTypes = operationTypes; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
}
