package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Column;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;

@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_instance_id")
    private ProductInstance productInstance;

    // v1 backward compatibility: Stage reference (kept for migration)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private Stage stage;

    // v2: Operation reference (replaces Stage concept)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id")
    private Operation operation;

    // v2: Assembly instance this task operates on
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_instance_id")
    private AssemblyInstance assemblyInstance;

    // v2: Series reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;

    // v2: Batch reference (null for individual tasks)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;


    // v2: Post (work station) reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_worker_id")
    private Worker assignedWorker;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    // v2: Task type (individual or batch)
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type")
    private OperationType taskType = OperationType.INDIVIDUAL;

    // v2: Priority
    @Enumerated(EnumType.STRING)
    private TaskPriority priority = TaskPriority.MEDIUM;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime dueDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // v2: Time tracking
    private Integer normativeTimeMinutes;
    private Integer actualTimeMinutes;

    // v2: Equipment and tools needed
    private String equipment;
    private String tools;

    private boolean missingMaterials = false;

    @ManyToMany
    @JoinTable(
        name = "task_missing_materials",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "material_id")
    )
    private Set<Material> missingMaterialsList = new HashSet<>();

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ProductInstance getProductInstance() { return productInstance; }
    public void setProductInstance(ProductInstance productInstance) { this.productInstance = productInstance; }

    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }

    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public AssemblyInstance getAssemblyInstance() { return assemblyInstance; }
    public void setAssemblyInstance(AssemblyInstance assemblyInstance) { this.assemblyInstance = assemblyInstance; }

    public Series getSeries() { return series; }
    public void setSeries(Series series) { this.series = series; }

    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }


    public Post getPost() { return post; }
    public void setPost(Post post) { this.post = post; }

    public Worker getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(Worker assignedWorker) { this.assignedWorker = assignedWorker; }

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

    public Set<Material> getMissingMaterialsList() { return missingMaterialsList; }
    public void setMissingMaterialsList(Set<Material> missingMaterialsList) { this.missingMaterialsList = missingMaterialsList; }
}
