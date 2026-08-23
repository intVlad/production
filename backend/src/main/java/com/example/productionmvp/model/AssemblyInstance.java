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
import java.util.UUID;

@Entity
@Table(name = "assembly_instances")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AssemblyInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_id")
    private Assembly assembly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_instance_id")
    private ProductInstance productInstance;

    @Enumerated(EnumType.STRING)
    private AssemblyInstanceStatus status = AssemblyInstanceStatus.PLANNED;

    private Integer currentOperationIndex = 0;

    public AssemblyInstance() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Assembly getAssembly() { return assembly; }
    public void setAssembly(Assembly assembly) { this.assembly = assembly; }

    public ProductInstance getProductInstance() { return productInstance; }
    public void setProductInstance(ProductInstance productInstance) { this.productInstance = productInstance; }

    public AssemblyInstanceStatus getStatus() { return status; }
    public void setStatus(AssemblyInstanceStatus status) { this.status = status; }

    public Integer getCurrentOperationIndex() { return currentOperationIndex; }
    public void setCurrentOperationIndex(Integer currentOperationIndex) { this.currentOperationIndex = currentOperationIndex; }
}
