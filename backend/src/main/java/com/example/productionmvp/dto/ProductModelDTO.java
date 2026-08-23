package com.example.productionmvp.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.example.productionmvp.model.ProductModel;

public class ProductModelDTO {
    private UUID id;
    private String name;
    private String description;
    private String version;
    private String specification;
    private String requiredEquipment;
    private String requiredTools;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;

    public ProductModelDTO() {}

    public ProductModelDTO(ProductModel model) {
        this.id = model.getId();
        this.name = model.getName();
        this.description = model.getDescription();
        this.version = model.getVersion();
        this.specification = model.getSpecification();
        this.requiredEquipment = model.getRequiredEquipment();
        this.requiredTools = model.getRequiredTools();
        this.createdAt = model.getCreatedAt();
        this.archivedAt = model.getArchivedAt();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getRequiredEquipment() { return requiredEquipment; }
    public void setRequiredEquipment(String requiredEquipment) { this.requiredEquipment = requiredEquipment; }
    public String getRequiredTools() { return requiredTools; }
    public void setRequiredTools(String requiredTools) { this.requiredTools = requiredTools; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
