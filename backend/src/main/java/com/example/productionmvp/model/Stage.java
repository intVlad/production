package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import java.util.UUID;

@Entity
public class Stage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "product_model_id")
    private ProductModel productModel;

    private String name;
    private Integer orderIndex;

    @ManyToOne
    @JoinColumn(name = "depends_on_stage_id")
    private Stage dependsOnStage;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductModel getProductModel() { return productModel; }
    public void setProductModel(ProductModel productModel) { this.productModel = productModel; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public Stage getDependsOnStage() { return dependsOnStage; }
    public void setDependsOnStage(Stage dependsOnStage) { this.dependsOnStage = dependsOnStage; }
}
