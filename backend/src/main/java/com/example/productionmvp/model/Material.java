package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String sku;

    private String unit;
    private Double normPerUnit;
    private Double availableStock = 0.0;
    private Double reservedQuantity = 0.0;
    private Double usedQuantity = 0.0;
    private Double minimumStock = 0.0;
    private String supplier;

    @Enumerated(EnumType.STRING)
    private SupplyStatus supplyStatus = SupplyStatus.SUFFICIENT;

    public Material() {}

    public Material(String name, String sku) {
        this.name = name;
        this.sku = sku;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getNormPerUnit() { return normPerUnit; }
    public void setNormPerUnit(Double normPerUnit) { this.normPerUnit = normPerUnit; }
    public Double getAvailableStock() { return availableStock; }
    public void setAvailableStock(Double availableStock) { this.availableStock = availableStock; }
    public Double getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Double reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Double getUsedQuantity() { return usedQuantity; }
    public void setUsedQuantity(Double usedQuantity) { this.usedQuantity = usedQuantity; }
    public Double getMinimumStock() { return minimumStock; }
    public void setMinimumStock(Double minimumStock) { this.minimumStock = minimumStock; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public SupplyStatus getSupplyStatus() { return supplyStatus; }
    public void setSupplyStatus(SupplyStatus supplyStatus) { this.supplyStatus = supplyStatus; }
}
