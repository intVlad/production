package com.example.productionmvp.dto;

import java.util.UUID;
import com.example.productionmvp.model.OperationType;

public class OperationRequestDTO {
    private String name;
    private String description;
    private Integer normativeTimeMinutes;
    private Integer orderIndex;
    private UUID sectionId;
    private UUID postId;
    private OperationType type;
    private String equipment;
    private String tools;
    private String requiredQualification;
    private UUID dependsOnOperationId;
    private UUID materialId;
    private Double materialQuantityPerUnit;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getNormativeTimeMinutes() { return normativeTimeMinutes; }
    public void setNormativeTimeMinutes(Integer normativeTimeMinutes) { this.normativeTimeMinutes = normativeTimeMinutes; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }

    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }

    public OperationType getType() { return type; }
    public void setType(OperationType type) { this.type = type; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }

    public String getRequiredQualification() { return requiredQualification; }
    public void setRequiredQualification(String requiredQualification) { this.requiredQualification = requiredQualification; }

    public UUID getDependsOnOperationId() { return dependsOnOperationId; }
    public void setDependsOnOperationId(UUID dependsOnOperationId) { this.dependsOnOperationId = dependsOnOperationId; }

    public UUID getMaterialId() { return materialId; }
    public void setMaterialId(UUID materialId) { this.materialId = materialId; }

    public Double getMaterialQuantityPerUnit() { return materialQuantityPerUnit; }
    public void setMaterialQuantityPerUnit(Double materialQuantityPerUnit) { this.materialQuantityPerUnit = materialQuantityPerUnit; }
}
