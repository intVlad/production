package com.example.productionmvp.dto;

import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.model.TaskPriority;
import com.example.productionmvp.model.OperationType;

import java.time.LocalDateTime;
import java.util.UUID;

public class TaskDTO {
    private UUID id;
    private UUID productInstanceId;
    private String productInstanceSerialNumber;
    private UUID operationId;
    private String operationName;
    private UUID assemblyInstanceId;
    private UUID seriesId;
    private String seriesName;
    private UUID batchId;
    private String batchNumber;
    private UUID postId;
    private String postName;
    private UUID assignedWorkerId;
    private String assignedWorkerName;
    private TaskStatus status;
    private OperationType taskType;
    private TaskPriority priority;
    private LocalDateTime createdAt;
    private LocalDateTime dueDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer normativeTimeMinutes;
    private Integer actualTimeMinutes;
    private String equipment;
    private String tools;
    private boolean missingMaterials;

    public TaskDTO() {}

    public TaskDTO(Task task) {
        this.id = task.getId();
        // task.getProductInstance() is the v1 field - createIndividualTask (the only real
        // task-creation path in the current assemblyInstance/series model) never sets it, only
        // task.getAssemblyInstance(). Without this fallback productInstanceSerialNumber was
        // null on every real task, which is what made the worker's "Виріб" field always show
        // "Невідомо" for tasks picked from a post's task list (no pallet scan involved).
        com.example.productionmvp.model.ProductInstance productInstance = task.getProductInstance();
        if (productInstance == null && task.getAssemblyInstance() != null) {
            productInstance = task.getAssemblyInstance().getProductInstance();
        }
        if (productInstance != null) {
            this.productInstanceId = productInstance.getId();
            this.productInstanceSerialNumber = productInstance.getSerialNumber();
        }
        if (task.getOperation() != null) {
            this.operationId = task.getOperation().getId();
            this.operationName = task.getOperation().getName();
        } else if (task.getStage() != null) {
            // Backward compatibility
            this.operationName = task.getStage().getName();
        }
        if (task.getAssemblyInstance() != null) {
            this.assemblyInstanceId = task.getAssemblyInstance().getId();
        }
        if (task.getSeries() != null) {
            this.seriesId = task.getSeries().getId();
            this.seriesName = task.getSeries().getNumber();
        }
        if (task.getBatch() != null) {
            this.batchId = task.getBatch().getId();
            this.batchNumber = task.getBatch().getNumber();
        }
        if (task.getPost() != null) {
            this.postId = task.getPost().getId();
            this.postName = task.getPost().getName();
        }
        if (task.getAssignedWorker() != null) {
            this.assignedWorkerId = task.getAssignedWorker().getId();
            this.assignedWorkerName = task.getAssignedWorker().getName();
        }
        this.status = task.getStatus();
        this.taskType = task.getTaskType();
        this.priority = task.getPriority();
        this.createdAt = task.getCreatedAt();
        this.dueDate = task.getDueDate();
        this.startedAt = task.getStartedAt();
        this.completedAt = task.getCompletedAt();
        this.normativeTimeMinutes = task.getNormativeTimeMinutes();
        this.actualTimeMinutes = task.getActualTimeMinutes();
        this.equipment = task.getEquipment();
        this.tools = task.getTools();
        this.missingMaterials = task.isMissingMaterials();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getProductInstanceId() { return productInstanceId; }
    public void setProductInstanceId(UUID productInstanceId) { this.productInstanceId = productInstanceId; }

    public String getProductInstanceSerialNumber() { return productInstanceSerialNumber; }
    public void setProductInstanceSerialNumber(String productInstanceSerialNumber) { this.productInstanceSerialNumber = productInstanceSerialNumber; }

    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public UUID getAssemblyInstanceId() { return assemblyInstanceId; }
    public void setAssemblyInstanceId(UUID assemblyInstanceId) { this.assemblyInstanceId = assemblyInstanceId; }

    public UUID getSeriesId() { return seriesId; }
    public void setSeriesId(UUID seriesId) { this.seriesId = seriesId; }

    public String getSeriesName() { return seriesName; }
    public void setSeriesName(String seriesName) { this.seriesName = seriesName; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }

    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }

    public UUID getAssignedWorkerId() { return assignedWorkerId; }
    public void setAssignedWorkerId(UUID assignedWorkerId) { this.assignedWorkerId = assignedWorkerId; }

    public String getAssignedWorkerName() { return assignedWorkerName; }
    public void setAssignedWorkerName(String assignedWorkerName) { this.assignedWorkerName = assignedWorkerName; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public OperationType getTaskType() { return taskType; }
    public void setTaskType(OperationType taskType) { this.taskType = taskType; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Integer getNormativeTimeMinutes() { return normativeTimeMinutes; }
    public void setNormativeTimeMinutes(Integer normativeTimeMinutes) { this.normativeTimeMinutes = normativeTimeMinutes; }

    public Integer getActualTimeMinutes() { return actualTimeMinutes; }
    public void setActualTimeMinutes(Integer actualTimeMinutes) { this.actualTimeMinutes = actualTimeMinutes; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }

    public boolean isMissingMaterials() { return missingMaterials; }
    public void setMissingMaterials(boolean missingMaterials) { this.missingMaterials = missingMaterials; }
}
