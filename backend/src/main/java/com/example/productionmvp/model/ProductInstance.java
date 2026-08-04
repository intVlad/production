package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.util.UUID;

@Entity
public class ProductInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "product_model_id")
    private ProductModel productModel;

    private String serialNumber;

    @Enumerated(EnumType.STRING)
    private InstanceStatus status;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductModel getProductModel() { return productModel; }
    public void setProductModel(ProductModel productModel) { this.productModel = productModel; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public InstanceStatus getStatus() { return status; }
    public void setStatus(InstanceStatus status) { this.status = status; }
}
