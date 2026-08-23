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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "defect_records")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DefectRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_instance_id")
    private AssemblyInstance assemblyInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_id")
    private Worker confirmedBy;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private DefectResolution resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_batch_id")
    private Batch replacementBatch;

    public DefectRecord() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AssemblyInstance getAssemblyInstance() { return assemblyInstance; }
    public void setAssemblyInstance(AssemblyInstance assemblyInstance) { this.assemblyInstance = assemblyInstance; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Worker getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Worker confirmedBy) { this.confirmedBy = confirmedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public DefectResolution getResolution() { return resolution; }
    public void setResolution(DefectResolution resolution) { this.resolution = resolution; }

    public Batch getReplacementBatch() { return replacementBatch; }
    public void setReplacementBatch(Batch replacementBatch) { this.replacementBatch = replacementBatch; }
}
