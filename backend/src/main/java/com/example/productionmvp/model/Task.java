package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;

@Entity
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "product_instance_id")
    private ProductInstance productInstance;

    @ManyToOne
    @JoinColumn(name = "stage_id")
    private Stage stage;

    @ManyToOne
    @JoinColumn(name = "work_area_id")
    private WorkArea workArea;

    @ManyToOne
    @JoinColumn(name = "assigned_worker_id")
    private Worker assignedWorker;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime dueDate;
    private boolean missingMaterials = false;

    @ManyToMany
    @JoinTable(
        name = "task_missing_materials",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "material_id")
    )
    private Set<Material> missingMaterialsList = new HashSet<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductInstance getProductInstance() { return productInstance; }
    public void setProductInstance(ProductInstance productInstance) { this.productInstance = productInstance; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public WorkArea getWorkArea() { return workArea; }
    public void setWorkArea(WorkArea workArea) { this.workArea = workArea; }
    public Worker getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(Worker assignedWorker) { this.assignedWorker = assignedWorker; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public boolean isMissingMaterials() { return missingMaterials; }
    public void setMissingMaterials(boolean missingMaterials) { this.missingMaterials = missingMaterials; }
    public Set<Material> getMissingMaterialsList() { return missingMaterialsList; }
    public void setMissingMaterialsList(Set<Material> missingMaterialsList) { this.missingMaterialsList = missingMaterialsList; }
}
