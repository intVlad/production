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
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.util.UUID;
import java.util.List;

@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ProductInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_model_id")
    private ProductModel productModel;

    private String serialNumber;

    @Enumerated(EnumType.STRING)
    private InstanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;

    private String modelVersion;
    private String currentStage;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "productInstance", cascade = CascadeType.ALL)
    private List<AssemblyInstance> assemblyInstances;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductModel getProductModel() { return productModel; }
    public void setProductModel(ProductModel productModel) { this.productModel = productModel; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public InstanceStatus getStatus() { return status; }
    public void setStatus(InstanceStatus status) { this.status = status; }
    public Series getSeries() { return series; }
    public void setSeries(Series series) { this.series = series; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public List<AssemblyInstance> getAssemblyInstances() { return assemblyInstances; }
    public void setAssemblyInstances(List<AssemblyInstance> assemblyInstances) { this.assemblyInstances = assemblyInstances; }
}
