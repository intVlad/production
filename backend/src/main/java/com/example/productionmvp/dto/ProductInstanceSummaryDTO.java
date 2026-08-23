package com.example.productionmvp.dto;

import com.example.productionmvp.model.InstanceStatus;
import com.example.productionmvp.model.ProductInstance;

import java.util.UUID;

public class ProductInstanceSummaryDTO {
    private UUID id;
    private String serialNumber;
    private InstanceStatus status;

    public ProductInstanceSummaryDTO() {}

    public ProductInstanceSummaryDTO(ProductInstance instance) {
        this.id = instance.getId();
        this.serialNumber = instance.getSerialNumber();
        this.status = instance.getStatus();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public InstanceStatus getStatus() { return status; }
    public void setStatus(InstanceStatus status) { this.status = status; }
}
