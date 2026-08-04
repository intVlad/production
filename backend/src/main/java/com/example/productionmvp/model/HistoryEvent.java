package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class HistoryEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDateTime timestamp = LocalDateTime.now();
    private String action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_instance_id")
    private ProductInstance productInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public ProductInstance getProductInstance() { return productInstance; }
    public void setProductInstance(ProductInstance productInstance) { this.productInstance = productInstance; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public Worker getWorker() { return worker; }
    public void setWorker(Worker worker) { this.worker = worker; }
}
