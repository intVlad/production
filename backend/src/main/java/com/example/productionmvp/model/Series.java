package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.FetchType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_model_id")
    private ProductModel productModel;

    private String modelVersion;
    private Integer plannedQuantity;
    private Integer actualQuantity = 0;

    @Enumerated(EnumType.STRING)
    private SeriesStatus status = SeriesStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    private SeriesPriority priority = SeriesPriority.MEDIUM;

    private LocalDateTime plannedStartDate;
    private LocalDateTime plannedEndDate;
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<ProductInstance> products = new ArrayList<>();

    public Series() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public ProductModel getProductModel() { return productModel; }
    public void setProductModel(ProductModel productModel) { this.productModel = productModel; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public Integer getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(Integer actualQuantity) { this.actualQuantity = actualQuantity; }

    public SeriesStatus getStatus() { return status; }
    public void setStatus(SeriesStatus status) { this.status = status; }

    public SeriesPriority getPriority() { return priority; }
    public void setPriority(SeriesPriority priority) { this.priority = priority; }

    public LocalDateTime getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDateTime plannedStartDate) { this.plannedStartDate = plannedStartDate; }

    public LocalDateTime getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDateTime plannedEndDate) { this.plannedEndDate = plannedEndDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ProductInstance> getProducts() { return products; }
    public void setProducts(List<ProductInstance> products) { this.products = products; }
}
